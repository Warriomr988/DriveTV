package com.panda.drivetv.ui.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panda.drivetv.data.model.DriveFile
import com.panda.drivetv.ui.formatSize
import com.panda.drivetv.ui.tv.TvCard

/**
 * Tarjeta grande y legible para TV: carpeta o vídeo, con foco visual claro
 * para el D-pad (borde + escala de [TvCard]).
 */
@Composable
fun FileCard(
    file: DriveFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (file.isFolder) "📁" else "🎬",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                val subtitle = when {
                    file.isFolder -> "Carpeta"
                    file.size != null -> formatSize(file.size)
                    else -> "Vídeo"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!file.isFolder && !file.isVideo) {
                Text("📄", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
