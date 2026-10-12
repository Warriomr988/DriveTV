package com.panda.drivetv.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.panda.drivetv.data.model.DriveFile
import com.panda.drivetv.data.model.SharedDrive
import com.panda.drivetv.data.repository.DriveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilesUiState(
    val loading: Boolean = false,
    /** Carpetas de la ubicación actual. */
    val folders: List<DriveFile> = emptyList(),
    /** Archivos (no carpetas) de la ubicación actual. */
    val files: List<DriveFile> = emptyList(),
    /** Solo en la raíz: todos los vídeos del Drive (búsqueda por mimeType). */
    val allVideos: List<DriveFile> = emptyList(),
    /** Solo en la raíz: unidades compartidas (v0.7.0). */
    val sharedDrives: List<SharedDrive> = emptyList(),
    val error: String? = null,
    val path: List<String> = listOf("Mi unidad")
) {
    val isRoot: Boolean get() = path.size <= 1
}

/** Resultado interno de la carga (4 listas). */
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * Explorador de Drive (etapa 4 de la guía): navegación por carpetas con pila.
 * En la raíz además se muestran todos los vídeos del Drive para no tener
 * que entrar carpeta por carpeta.
 */
class FilesViewModel(
    private val repository: DriveRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private val stack = ArrayDeque<Pair<String, String>>()

    init {
        openRoot()
    }

    fun openRoot() {
        stack.clear()
        stack.addLast(ROOT_ID to "Mi unidad")
        load()
    }

    fun openFolder(file: DriveFile) {
        stack.addLast(file.id to file.name)
        load()
    }

    /** Entra a una unidad compartida como si fuera una carpeta raíz (v0.7.0). */
    fun openSharedDrive(drive: SharedDrive) {
        stack.addLast(drive.id to drive.name)
        load()
    }

    /**
     * @return true si retrocedió dentro de Drive; false si ya está en la raíz
     * (la UI decide entonces si sale de la pantalla).
     */
    fun goBack(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        load()
        return true
    }

    fun retry() = load()

    private fun load() {
        val (id, _) = stack.last()
        val path = stack.map { it.second }
        val atRoot = stack.size == 1
        _state.value = FilesUiState(loading = true, path = path)
        viewModelScope.launch {
            runCatching {
                val items = repository.listFolder(id)
                val (folders, rest) = items.partition { it.isFolder }
                val videos = if (atRoot) repository.listAllVideos() else emptyList()
                // Las unidades compartidas no deben romper la raíz si fallan.
                val drives = if (atRoot) {
                    runCatching { repository.listSharedDrives() }.getOrDefault(emptyList())
                } else emptyList()
                Quad(
                    folders.sortedBy { it.name.lowercase() },
                    rest.sortedBy { it.name.lowercase() },
                    videos.sortedBy { it.name.lowercase() },
                    drives.sortedBy { it.name.lowercase() }
                )
            }.onSuccess { (folders, files, videos, drives) ->
                _state.value = FilesUiState(
                    folders = folders,
                    files = files,
                    allVideos = videos,
                    sharedDrives = drives,
                    path = path
                )
            }.onFailure {
                _state.value = FilesUiState(
                    error = it.message ?: "No se pudo listar la carpeta.",
                    path = path
                )
            }
        }
    }

    companion object {
        const val ROOT_ID = "root"

        fun factory(repository: DriveRepository) = viewModelFactory {
            initializer { FilesViewModel(repository) }
        }
    }
}
