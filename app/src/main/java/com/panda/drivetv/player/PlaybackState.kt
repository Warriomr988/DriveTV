package com.panda.drivetv.player

/**
 * Estado observable de la reproducción para la UI de TV (etapa 6).
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: PlayerError? = null
)

/** Error ya traducido a mensaje para TV (ver tabla de la guía, §5.7/§6.12). */
data class PlayerError(
    val message: String,
    val canRetry: Boolean = true,
    val isAuthError: Boolean = false,
    /** true si el decodificador rechazó el formato (v0.7.3: ofrece Player VIP). */
    val isDecoderError: Boolean = false
)
