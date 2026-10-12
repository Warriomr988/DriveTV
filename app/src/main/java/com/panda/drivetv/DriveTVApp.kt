package com.panda.drivetv

import android.app.Application
import com.panda.drivetv.auth.AuthManager
import com.panda.drivetv.auth.PrefsTokenStore
import com.panda.drivetv.auth.TokenStore
import com.panda.drivetv.data.remote.DriveApi
import com.panda.drivetv.data.remote.HttpDriveApi
import com.panda.drivetv.data.repository.DriveRepository
import com.panda.drivetv.data.tmdb.TmdbApi
import com.panda.drivetv.library.MovieRepository
import com.panda.drivetv.player.DeviceCapabilities
import com.panda.drivetv.player.ResumeManager
import com.panda.drivetv.settings.AppSettings

/**
 * Punto de entrada de la app. Contiene el "DI manual" (singletons perezosos)
 * para mantener separadas las capas: auth → data → player → ui.
 *
 * Regla del proyecto: el access token NUNCA viaja en Intents; las pantallas
 * lo obtienen siempre a través de [authManager].
 */
class DriveTVApp : Application() {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var authManager: AuthManager
        private set
    lateinit var driveRepository: DriveRepository
        private set
    lateinit var resumeManager: ResumeManager
        private set
    lateinit var deviceCapabilities: DeviceCapabilities
        private set
    lateinit var appSettings: AppSettings
        private set
    lateinit var movieRepository: MovieRepository
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = PrefsTokenStore(this)
        authManager = AuthManager(this, tokenStore)
        val api: DriveApi = HttpDriveApi(tokenProvider = { authManager.accessToken() })
        driveRepository = DriveRepository(api)
        resumeManager = ResumeManager(this)
        deviceCapabilities = DeviceCapabilities.fromDevice(this)
        appSettings = AppSettings(this)
        movieRepository = MovieRepository(this, driveRepository, TmdbApi())
    }
}
