package com.panda.drivetv.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Frontera del reproductor (etapa 6, §6.11 de la guía).
 * Media3 es la ruta principal; [MpvPlayer] es el fallback futuro.
 */
interface PlayerManager {
    val state: StateFlow<PlaybackState>

    fun prepare(fileId: String, mediaUrl: String, resumeMs: Long = 0L)
    fun play()
    fun pause()
    fun toggle()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)

    fun audioTracks(): List<TrackInfo>
    fun subtitleTracks(): List<TrackInfo>
    fun selectAudio(track: TrackInfo)
    fun selectSubtitle(track: TrackInfo)
    fun disableSubtitles()

    /** Alterna la proporción del vídeo; devuelve la etiqueta del modo activo. */
    fun cycleAspectRatio(): String

    /** Reintenta la reproducción actual desde la última posición. */
    fun retry()

    fun release()
}
