package com.panda.drivetv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.panda.drivetv.DriveTVApp
import com.panda.drivetv.library.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = false,
    /** Texto de progreso ("12/48") mientras carga. */
    val progress: String? = null,
    val movies: List<Movie> = emptyList(),
    val error: String? = null,
    /** true si no hay API key de TMDB: se muestra la biblioteca sin carátulas. */
    val needsApiKey: Boolean = false
)

/**
 * Biblioteca estilo Netflix (v0.6.0). Se crea una sola vez por actividad para
 * que LibraryScreen y MovieDetailScreen compartan la lista.
 */
class LibraryViewModel(
    private val app: DriveTVApp
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** "Mi lista" (v0.7.0): ids de Drive en favoritos. */
    private val _favorites = MutableStateFlow(app.appSettings.getFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private var started = false

    /**
     * Carga solo la primera vez que la biblioteca se muestra (el ViewModel
     * vive en la actividad y puede crearse antes del login).
     */
    fun ensureLoaded() {
        if (!started) {
            started = true
            load()
        }
    }

    fun load() {
        _state.value = LibraryUiState(loading = true)
        viewModelScope.launch {
            runCatching {
                val apiKey = app.appSettings.getTmdbApiKey()
                val movies = app.movieRepository.loadLibrary(apiKey) { done, total ->
                    _state.value = _state.value.copy(progress = "$done/$total")
                }
                _state.value = LibraryUiState(
                    movies = movies,
                    needsApiKey = apiKey.isNullOrBlank()
                )
            }.onFailure { e ->
                _state.value = LibraryUiState(
                    error = e.message ?: "No se pudo cargar la biblioteca."
                )
            }
        }
    }

    fun refresh() {
        _state.value = LibraryUiState(loading = true)
        viewModelScope.launch {
            runCatching {
                val apiKey = app.appSettings.getTmdbApiKey()
                val movies = app.movieRepository.loadLibrary(apiKey, forceRefresh = true) { done, total ->
                    _state.value = _state.value.copy(progress = "$done/$total")
                }
                _state.value = LibraryUiState(
                    movies = movies,
                    needsApiKey = apiKey.isNullOrBlank()
                )
            }.onFailure { e ->
                _state.value = LibraryUiState(
                    error = e.message ?: "No se pudo actualizar la biblioteca."
                )
            }
        }
    }

    fun movieById(driveId: String): Movie? =
        _state.value.movies.firstOrNull { it.driveId == driveId }

    /** Películas marcadas en "Mi lista", en el orden de la biblioteca. */
    fun favoriteMovies(): List<Movie> {
        val fav = _favorites.value
        return _state.value.movies.filter { fav.contains(it.driveId) }
    }

    fun toggleFavorite(movie: Movie) {
        viewModelScope.launch(Dispatchers.IO) {
            app.appSettings.toggleFavorite(movie.driveId)
            _favorites.value = app.appSettings.getFavorites()
        }
    }

    companion object {
        fun factory(app: DriveTVApp): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LibraryViewModel(app) }
            }
    }
}
