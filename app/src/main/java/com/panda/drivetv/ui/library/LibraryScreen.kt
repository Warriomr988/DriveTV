package com.panda.drivetv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.panda.drivetv.DriveTVApp
import com.panda.drivetv.library.Movie
import com.panda.drivetv.ui.tv.TvButton

/**
 * Biblioteca estilo Netflix (v0.6.0): cuadrícula de carátulas.
 * D-pad: flechas para moverse, OK para ver detalles, Back para volver al inicio.
 */
@Composable
fun LibraryScreen(
    app: DriveTVApp,
    vm: LibraryViewModel,
    onOpenMovie: (Movie) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    BackHandler { onBack() }

    LaunchedEffect(Unit) { vm.ensureLoaded() }

    // Si volvió de Ajustes con una API key nueva, recargar para traer carátulas.
    val hasKeyNow = !app.appSettings.getTmdbApiKey().isNullOrBlank()
    LaunchedEffect(hasKeyNow, state.needsApiKey, state.loading) {
        if (hasKeyNow && state.needsApiKey && !state.loading && state.movies.isNotEmpty()) {
            vm.load()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra superior: título + menú.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎬 Biblioteca",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = onOpenSearch) { Text("🔍") }
                TvButton(onClick = onOpenFiles) { Text("📁 Archivos") }
                TvButton(onClick = { vm.refresh() }) { Text("🔄") }
                TvButton(onClick = onOpenSettings) { Text("⚙") }
            }
        }

        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.progress?.let { "Buscando carátulas… $it" }
                                ?: "Cargando películas…",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        TvButton(onClick = { vm.load() }) { Text("Reintentar") }
                    }
                }
            }
            state.movies.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay vídeos en tu Drive",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            else -> {
                if (state.needsApiKey) {
                    ApiKeyBanner(onOpenSettings = onOpenSettings)
                }
                MovieGrid(
                    movies = state.movies,
                    onOpenMovie = onOpenMovie
                )
            }
        }
    }
}

@Composable
private fun ApiKeyBanner(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Agrega tu API key de TMDB en Ajustes para ver las carátulas.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TvButton(onClick = onOpenSettings) { Text("Ir a Ajustes") }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun MovieGrid(
    movies: List<Movie>,
    onOpenMovie: (Movie) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(movies) {
        if (movies.isNotEmpty()) {
            // Pequeña espera a que el grid mida sus items antes de pedir foco.
            kotlinx.coroutines.delay(300)
            runCatching { firstFocus.requestFocus() }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        itemsIndexed(movies, key = { _, m -> m.driveId }) { index, movie ->
            PosterCard(
                movie = movie,
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { onOpenMovie(movie) }
            )
        }
    }
}
