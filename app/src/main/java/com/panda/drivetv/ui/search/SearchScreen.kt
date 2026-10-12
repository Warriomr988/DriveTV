package com.panda.drivetv.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.panda.drivetv.library.Movie
import com.panda.drivetv.ui.library.LibraryViewModel
import com.panda.drivetv.ui.library.PosterCard

/**
 * Buscador local (v0.7.0): filtra la biblioteca por título.
 * D-pad: OK en el campo abre el teclado, flechas en los resultados.
 */
@Composable
fun SearchScreen(
    vm: LibraryViewModel,
    onOpenMovie: (Movie) -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        vm.ensureLoaded()
        runCatching { fieldFocus.requestFocus() }
    }

    val results = remember(query, state.movies) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else state.movies.filter { it.title.lowercase().contains(q) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "🔍 Buscar",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Título de película o serie…") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .focusRequester(fieldFocus)
        )
        Spacer(Modifier.height(16.dp))

        when {
            query.isBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Escribe para buscar en tu biblioteca",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin resultados para \"$query\"",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(results, key = { it.driveId }) { movie ->
                        PosterCard(movie = movie, onClick = { onOpenMovie(movie) })
                    }
                }
            }
        }
    }
}
