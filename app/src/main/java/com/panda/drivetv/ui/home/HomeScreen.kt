package com.panda.drivetv.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.panda.drivetv.DriveTVApp
import com.panda.drivetv.library.Movie
import com.panda.drivetv.ui.library.LibraryViewModel
import com.panda.drivetv.ui.library.PosterCard
import com.panda.drivetv.ui.tv.TvButton

/**
 * Inicio estilo Netflix (v0.7.0): hero con backdrop + filas horizontales.
 * D-pad: arriba/abajo entre hero y filas, izquierda/derecha dentro de filas.
 */
@Composable
fun HomeScreen(
    app: DriveTVApp,
    vm: LibraryViewModel,
    onOpenMovie: (Movie) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit
) {
    val state by vm.state.collectAsState()
    val favorites by vm.favorites.collectAsState()

    BackHandler { onExit() }

    LaunchedEffect(Unit) { vm.ensureLoaded() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra superior.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DriveTV",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFE50914),
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = onOpenSearch) { Text("🔍 Buscar") }
                TvButton(onClick = onOpenLibrary) { Text("🎞 Todas") }
                TvButton(onClick = onOpenFiles) { Text("📁 Archivos") }
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
                    Text("No hay vídeos en tu Drive", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                val movies = state.movies
                val featured = movies.firstOrNull { it.backdropUrl != null } ?: movies.first()
                val favMovies = vm.favoriteMovies()
                val genreRows = movies
                    .flatMap { it.genres }
                    .groupingBy { it }.eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(4)
                    .mapNotNull { (genre, count) ->
                        if (count >= 2) genre to movies.filter { it.genres.contains(genre) }
                        else null
                    }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "hero") {
                        Hero(
                            movie = featured,
                            isFavorite = favorites.contains(featured.driveId),
                            onToggleFavorite = { vm.toggleFavorite(featured) },
                            onPlay = { onPlayMovie(featured) },
                            onOpenMovie = onOpenMovie
                        )
                    }
                    if (favMovies.isNotEmpty()) {
                        item(key = "row-fav") {
                            MovieRow(title = "⭐ Mi lista", movies = favMovies, onOpenMovie = onOpenMovie)
                        }
                    }
                    item(key = "row-all") {
                        MovieRow(title = "🎬 Películas y series", movies = movies, onOpenMovie = onOpenMovie)
                    }
                    items(genreRows, key = { (genre, _) -> "row-$genre" }) { (genre, list) ->
                        MovieRow(title = genre, movies = list, onOpenMovie = onOpenMovie)
                    }
                    item(key = "bottom-space") { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

/** Hero: backdrop a todo lo ancho + ficha + botones (como el banner de Netflix). */
@Composable
private fun Hero(
    movie: Movie,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    onOpenMovie: (Movie) -> Unit
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(movie.driveId) {
        runCatching { playFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        if (movie.backdropUrl != null) {
            AsyncImage(
                model = movie.backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.05f)))
        }
        // Degradado inferior para que el texto se lea.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f),
                            Color(0xFF121212)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 48.dp, vertical = 24.dp)
                .fillMaxWidth(0.65f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (movie.isSeries) "SERIE" else "PELÍCULA",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFE50914)
            )
            Text(
                text = movie.title,
                style = MaterialTheme.typography.displayMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildList {
                movie.year?.let { add(it.toString()) }
                if (movie.genres.isNotEmpty()) add(movie.genres.joinToString(" · "))
            }.joinToString("  •  ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            if (movie.overview.isNotBlank()) {
                Text(
                    text = movie.overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playFocus)
                ) {
                    Text("▶ Reproducir")
                }
                TvButton(onClick = onToggleFavorite) {
                    Text(if (isFavorite) "✓ En mi lista" else "+ Mi lista")
                }
                TvButton(onClick = { onOpenMovie(movie) }) {
                    Text("ⓘ Detalles")
                }
            }
        }
    }
}

/** Fila horizontal de pósters con título (como las filas de Netflix). */
@Composable
private fun MovieRow(
    title: String,
    movies: List<Movie>,
    onOpenMovie: (Movie) -> Unit
) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(movies, key = { it.driveId }) { movie ->
                PosterCard(
                    movie = movie,
                    modifier = Modifier.width(130.dp),
                    showTitle = false,
                    onClick = { onOpenMovie(movie) }
                )
            }
        }
    }
}
