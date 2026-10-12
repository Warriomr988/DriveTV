package com.panda.drivetv.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panda.drivetv.DriveTVApp
import com.panda.drivetv.data.model.DriveFile
import com.panda.drivetv.data.model.SharedDrive
import com.panda.drivetv.ui.tv.TvButton
import com.panda.drivetv.ui.tv.TvCard

/**
 * Explorador de Drive para TV (etapa 4 de la guía).
 * D-pad: arriba/abajo para moverse, OK para entrar/reproducir, Back para subir.
 *
 * En la raíz: carpetas + todos los vídeos del Drive (sin entrar a carpetas).
 */
@Composable
fun FilesScreen(
    app: DriveTVApp,
    onPlayVideo: (DriveFile) -> Unit,
    onOpenSettings: () -> Unit,
    onBackAtRoot: () -> Unit
) {
    val vm: FilesViewModel = viewModel(factory = FilesViewModel.factory(app.driveRepository))
    val state by vm.state.collectAsState()

    BackHandler {
        if (!vm.goBack()) onBackAtRoot()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.path.joinToString(" › "),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TvButton(onClick = onOpenSettings) {
                Text("⚙ Ajustes")
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        TvButton(onClick = { vm.retry() }) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            state.isRoot -> RootContent(
                state = state,
                onOpenFolder = { vm.openFolder(it) },
                onOpenDrive = { vm.openSharedDrive(it) },
                onPlayVideo = onPlayVideo
            )
            else -> FolderContent(
                state = state,
                onOpenFolder = { vm.openFolder(it) },
                onPlayVideo = onPlayVideo
            )
        }
    }
}

@Composable
private fun RootContent(
    state: FilesUiState,
    onOpenFolder: (DriveFile) -> Unit,
    onOpenDrive: (SharedDrive) -> Unit,
    onPlayVideo: (DriveFile) -> Unit
) {
    val isEmpty = state.folders.isEmpty() && state.files.isEmpty() &&
        state.allVideos.isEmpty() && state.sharedDrives.isEmpty()
    if (isEmpty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tu Drive está vacío", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.sharedDrives.isNotEmpty()) {
            item(key = "header-drives") { SectionTitle("💽 Unidades compartidas") }
            items(state.sharedDrives, key = { "drive-${it.id}" }) { drive ->
                DriveCard(drive = drive, onClick = { onOpenDrive(drive) })
            }
        }
        if (state.folders.isNotEmpty()) {
            item(key = "header-folders") { SectionTitle("📁 Carpetas") }
            items(state.folders, key = { "folder-${it.id}" }) { file ->
                FileCard(file = file, onClick = { onOpenFolder(file) })
            }
        }
        if (state.files.isNotEmpty()) {
            item(key = "header-files") { SectionTitle("📄 Archivos") }
            items(state.files, key = { "file-${it.id}" }) { file ->
                FileCard(
                    file = file,
                    onClick = { if (file.isVideo) onPlayVideo(file) }
                )
            }
        }
        if (state.allVideos.isNotEmpty()) {
            item(key = "header-videos") { SectionTitle("🎬 Todos los vídeos") }
            items(state.allVideos, key = { "video-${it.id}" }) { file ->
                FileCard(file = file, onClick = { onPlayVideo(file) })
            }
        }
    }
}

@Composable
private fun FolderContent(
    state: FilesUiState,
    onOpenFolder: (DriveFile) -> Unit,
    onPlayVideo: (DriveFile) -> Unit
) {
    if (state.folders.isEmpty() && state.files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Carpeta vacía", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.folders + state.files, key = { it.id }) { file ->
            FileCard(
                file = file,
                onClick = {
                    if (file.isFolder) onOpenFolder(file)
                    else if (file.isVideo) onPlayVideo(file)
                }
            )
        }
    }
}

/** Tarjeta de unidad compartida (v0.7.0). */
@Composable
private fun DriveCard(
    drive: SharedDrive,
    onClick: () -> Unit
) {
    TvCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("💽", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = drive.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
