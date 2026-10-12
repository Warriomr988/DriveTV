package com.panda.drivetv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.panda.drivetv.DriveTVApp
import com.panda.drivetv.ui.tv.TvButton

/**
 * Ajustes (etapa 6): sesión, reanudación, capacidades del dispositivo.
 */
@Composable
fun SettingsScreen(
    app: DriveTVApp,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var notice by remember { mutableStateOf<String?>(null) }
    var tmdbKey by remember { mutableStateOf(app.appSettings.getTmdbApiKey().orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.displaySmall)

        Text(
            "Sesión: ${if (app.authManager.isSignedIn()) "activa ✅" else "inactiva"}",
            style = MaterialTheme.typography.bodyLarge
        )

        TvButton(onClick = {
            app.authManager.signOut()
            onSignOut()
        }) {
            Text("Cerrar sesión")
        }

        TvButton(onClick = {
            app.resumeManager.clearAll()
            notice = "Historial de reanudación borrado."
        }) {
            Text("Borrar historial de reanudación")
        }

        notice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))
        Text("Biblioteca (TMDB)", style = MaterialTheme.typography.titleMedium)
        Text(
            "API key de themoviedb.org para las carátulas de la biblioteca. " +
                "Sin key, la biblioteca muestra los títulos sin imágenes.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = tmdbKey,
            onValueChange = { tmdbKey = it },
            label = { Text("TMDB API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TvButton(onClick = {
            app.appSettings.setTmdbApiKey(tmdbKey)
            notice = if (tmdbKey.isBlank()) "API key borrada." else "API key guardada."
        }) {
            Text("Guardar API key")
        }

        Spacer(Modifier.height(8.dp))
        Text("Capacidades del dispositivo", style = MaterialTheme.typography.titleMedium)
        Text(
            app.deviceCapabilities.summary(),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "DriveTV v0.7.3 · Inicio Netflix · com.panda.drivetv",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
