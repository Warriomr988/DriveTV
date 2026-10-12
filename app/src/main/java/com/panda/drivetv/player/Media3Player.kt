package com.panda.drivetv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.panda.drivetv.auth.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reproductor principal con Media3/ExoPlayer (etapas 5 y 6 de la guía).
 *
 * - Fuente de datos: [AuthenticatingDataSource] sobre DefaultHttpDataSource
 *   (Bearer + Range/seek, lectura progresiva: no descarga el archivo completo).
 * - Buffers iniciales según la guía: 20 s mín / 60 s máx para empezar.
 * - Los errores se traducen a mensajes de TV (tabla §5.7/§6.12); un 401 se
 *   marca como [PlayerError.isAuthError] para que la UI refresque el token
 *   y reintente una vez.
 */
class Media3Player(
    context: Context,
    private val authManager: AuthManager
) : PlayerManager {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val trackManager: TrackManager

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var progressJob: Job? = null
    private var lastFileId: String? = null
    private var lastMediaUrl: String? = null
    private var playerViewRef: PlayerView? = null

    // Proporciones: Ajustar (barras) → Llenar (recorta) → Zoom.
    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Ajustar",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Llenar",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom"
    )
    private var resizeModeIndex = 0

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
        .setAllowCrossProtocolRedirects(true)

    private val dataSourceFactory = androidx.media3.datasource.DataSource.Factory {
        AuthenticatingDataSource(
            upstream = httpFactory.createDataSource(),
            tokenProvider = { authManager.accessToken() }
        )
    }

    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    // Buffer ampliado para remuxes 4K de alto bitrate: acumula hasta 90 s de vídeo
    // (con tope de 384 MB de RAM) para pasar los picos sin pausarse. A cambio,
    // el arranque tarda unos segundos más.
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 30_000,
            /* maxBufferMs = */ 90_000,
            /* bufferForPlaybackMs = */ 5_000,
            /* bufferForPlaybackAfterRebufferMs = */ 10_000
        )
        .setTargetBufferBytes(384 * 1024 * 1024)
        .build()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            publishProgress()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                error = mapError(error)
            )
        }
    }

    // v0.7.3: si el decodificador de hardware rechaza el formato (p. ej.
    // Dolby Vision perfil 7), ExoPlayer prueba con otro decodificador
    // (base HDR10/software) en vez de fallar de inmediato.
    private val renderersFactory = DefaultRenderersFactory(appContext)
        .setEnableDecoderFallback(true)

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setRenderersFactory(renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .build()
        .also { player ->
            player.addListener(playerListener)
            player.playWhenReady = true
        }

    init {
        trackManager = TrackManager(exoPlayer)
        startProgressLoop()
    }

    /** Conecta la superficie de vídeo (PlayerView) al reproductor. */
    fun bindView(view: PlayerView) {
        playerViewRef = view
        view.player = exoPlayer
        view.useController = false // Controles propios de TV (D-pad)
    }

    /**
     * Alterna la proporción: Ajustar (con barras) → Llenar (recorta) → Zoom.
     * @return etiqueta del modo activo, para mostrarla en el botón.
     */
    override fun cycleAspectRatio(): String {
        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
        val (mode, label) = resizeModes[resizeModeIndex]
        playerViewRef?.resizeMode = mode
        return label
    }

    override fun prepare(fileId: String, mediaUrl: String, resumeMs: Long) {
        lastFileId = fileId
        lastMediaUrl = mediaUrl
        _state.value = PlaybackState(isBuffering = true)
        val item = MediaItem.fromUri(mediaUrl)
        exoPlayer.setMediaItem(item)
        if (resumeMs > 0) {
            exoPlayer.seekTo(resumeMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /** Reintenta la reproducción actual (usado tras refrescar el token). */
    override fun retry() {
        val url = lastMediaUrl ?: return
        val pos = _state.value.positionMs
        prepare(lastFileId ?: "", url, pos)
    }

    override fun play() {
        exoPlayer.playWhenReady = true
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun toggle() {
        if (exoPlayer.isPlaying) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L)))
        publishProgress()
    }

    override fun seekBy(deltaMs: Long) {
        seekTo(exoPlayer.currentPosition + deltaMs)
    }

    override fun audioTracks(): List<TrackInfo> = trackManager.audioTracks()
    override fun subtitleTracks(): List<TrackInfo> = trackManager.subtitleTracks()
    override fun selectAudio(track: TrackInfo) = trackManager.selectAudio(track)
    override fun selectSubtitle(track: TrackInfo) = trackManager.selectSubtitle(track)
    override fun disableSubtitles() = trackManager.disableSubtitles()

    override fun release() {
        progressJob?.cancel()
        scope.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }

    // ---------- interno ----------

    private fun startProgressLoop() {
        progressJob = scope.launch {
            while (isActive) {
                publishProgress()
                delay(500)
            }
        }
    }

    private fun publishProgress() {
        val current = _state.value
        _state.value = current.copy(
            isPlaying = exoPlayer.isPlaying,
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = exoPlayer.duration.coerceAtLeast(0L).takeIf { it != Long.MIN_VALUE } ?: 0L,
            error = if (exoPlayer.playerError == null) null else current.error
        )
    }

    private fun mapError(error: PlaybackException): PlayerError {
        val cause = error.cause
        val msg = cause?.message.orEmpty()
        // HttpDataSource$InvalidResponseCodeException → "Response code: 401"
        val code = Regex("Response code: (\\d{3})").find(msg)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            code == 401 -> PlayerError(
                message = "La sesión de Google expiró. Inicia sesión nuevamente.",
                canRetry = true,
                isAuthError = true
            )
            code == 403 -> PlayerError(
                message = "No tienes permiso para reproducir este archivo.",
                canRetry = false
            )
            code == 404 -> PlayerError(
                message = "El archivo ya no está disponible en Drive.",
                canRetry = false
            )
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ->
                PlayerError(
                    message = "Este dispositivo no puede reproducir este formato con el reproductor actual.",
                    canRetry = false,
                    isDecoderError = true
                )
            cause is java.net.UnknownHostException -> PlayerError(
                message = "No hay conexión. Comprueba la red e intenta nuevamente."
            )
            else -> PlayerError(
                message = "Error de reproducción. Intenta de nuevo."
            )
        }
    }
}
