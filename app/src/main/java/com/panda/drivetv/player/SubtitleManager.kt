package com.panda.drivetv.player

/**
 * Gestión de subtítulos externos SRT/VTT (etapa 6, §6.9 — iteración posterior).
 *
 * v0.1.0: los subtítulos incluidos en el contenedor se manejan con
 * [TrackManager] (activar/desactivar, idioma). Esta clase queda como punto de
 * extensión para añadir SRT/VTT externos con validación previa.
 */
object SubtitleManager {
    /** Valida que una URL de subtítulo externo sea segura antes de usarla. */
    fun isSafeSubtitleUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.startsWith("https://") &&
            (lower.endsWith(".srt") || lower.endsWith(".vtt"))) &&
            !lower.contains("..")
    }
}
