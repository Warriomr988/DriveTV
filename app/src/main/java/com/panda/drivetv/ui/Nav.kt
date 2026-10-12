package com.panda.drivetv.ui

/** Navegación simple por estado (sin librería de navegación). */
sealed interface Screen {
    data object SignIn : Screen
    /** v0.7.0: el inicio estilo Netflix es la pantalla inicial. */
    data object Home : Screen
    data object Library : Screen
    data object Search : Screen
    data class MovieDetail(val driveId: String) : Screen
    data object Files : Screen
    /** Motor de reproducción (v0.8.0): Media3 o MPV/libmpv. */
    enum class PlayerEngine { MEDIA3, MPV }

    data class Player(
        val fileId: String,
        val name: String,
        val engine: PlayerEngine = PlayerEngine.MEDIA3
    ) : Screen
    data object Settings : Screen
}
