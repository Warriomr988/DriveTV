package com.panda.drivetv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.panda.drivetv.library.Movie
import com.panda.drivetv.library.MovieVersion
import com.panda.drivetv.ui.tv.TvButton
import com.panda.drivetv.ui.tv.tvFocus

/**
 * Detalle de película/serie (v0.6.0+): carátula + ficha + botones.
 * D-pad: OK en "▶ Reproducir", Back para volver.
 */
@Composable
fun MovieDetailScreen(
    movie: Movie,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: (MovieVersion) -> Unit,
    onOpenVip: (MovieVersion) -> Unit,
    onPlayMpv: (MovieVersion) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { playFocus.requestFocus() }
    }
    // v0.7.1: versiones de la misma película (4K Remux, 1080p…).
    val versions = remember(movie.driveId) { movie.allVersions() }
    var selectedIdx by remember(movie.driveId) { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Cabecera con backdrop (si hay) y degradado.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            if (movie.backdropUrl != null) {
                AsyncImage(
                    model = movie.backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.05f))
                )
            }
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
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Carátula.
            if (movie.posterUrl != null) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    modifier = Modifier
                        .width(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(32.dp))

            // Ficha.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (movie.isSeries) "SERIE" else "PELÍCULA",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFE50914)
                )
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.displaySmall,
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
                        color = Color.White.copy(alpha = 0.85f)
                    )
                } else {
                    Text(
                        text = movie.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                if (versions.size > 1) {
                    Text(
                        text = "Versión:",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        versions.forEachIndexed { i, v ->
                            VersionChip(
                                label = v.label,
                                selected = i == selectedIdx,
                                onClick = { selectedIdx = i }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton(
                        onClick = { onPlay(versions[selectedIdx.coerceIn(versions.indices)]) },
                        modifier = Modifier.focusRequester(playFocus)
                    ) {
                        Text("▶ Reproducir")
                    }
                    // v0.7.3: Dolby Vision puede tumbar el decodificador del
                    // TV; Player VIP (externo) lo maneja mejor.
                    if (movie.dolbyVision) {
                        TvButton(
                            onClick = { onOpenVip(versions[selectedIdx.coerceIn(versions.indices)]) }
                        ) {
                            Text("📺 Player VIP")
                        }
                    }
                    // v0.8.0: motor alternativo libmpv (interno).
                    TvButton(
                        onClick = { onPlayMpv(versions[selectedIdx.coerceIn(versions.indices)]) }
                    ) {
                        Text("▶ MPV")
                    }
                    TvButton(onClick = onToggleFavorite) {
                        Text(if (isFavorite) "✓ En mi lista" else "+ Mi lista")
                    }
                    TvButton(onClick = onBack) {
                        Text("Atrás")
                    }
                }
            }
        }
    }
}

/** Chip de versión/calidad (v0.7.1): D-pad con foco, OK para elegir. */
@Composable
private fun VersionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .tvFocus()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) Color(0xFFE50914)
                else Color.White.copy(alpha = 0.12f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
    }
}
