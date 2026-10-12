package com.panda.drivetv.player

import android.content.Context
import android.view.Surface
import dev.jdtech.mpv.MPVLib
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
 * Reproductor alternativo con libmpv (v0.8.0 "MPV").
 *
 * Implementa [PlayerManager] sobre `dev.jdtech.mpv:libmpv` (AAR precompilado
 * con FFmpeg + mpv; sin NDK). Se usa para archivos que Media3 no puede
 * decodificar (p. ej. Dolby Vision perfil 7 que tumba el MediaCodec del TV).
 *
 * - La URL que recibe [prepare] debe ser reproducible sin auth extra: la UI
 *   le pasa la URL del proxy local ([VipBridge.proxyUrl]), que inyecta el
 *   Bearer <redacted> y soporta Range/seek.
 * - mpv es singleton por proceso ([MPVLib] es estático): se inicializa una
 *   vez y se reutiliza con `loadfile`; no se destruye en [release] para
 *   evitar ciclos create/destroy.
 * - Subtítulos y OSD los dibuja el propio mpv sobre el vídeo.
 */
class MpvPlayer(
    context: Context
) : PlayerManager {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var progressJob: Job? = null
    private var lastFileId: String? = null
    private var lastMediaUrl: String? = null
    private var pendingSeekMs: Long = 0L
    private var intentionalStop = false
    private var initialized = false

    // Proporciones: Ajustar → Llenar → Zoom (paridad con Media3Player).
    private var aspectIndex = 0

    private val eventObserver = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) = Unit
        override fun eventProperty(property: String, value: Long) = Unit
        override fun eventProperty(property: String, value: Double) = Unit
        override fun eventProperty(property: String, value: Boolean) = Unit
        override fun eventProperty(property: String, value: String) = Unit
        override fun event(eventCode: Int) {
            when (eventCode) {
                MPVLib.MPV_EVENT_FILE_LOADED -> {
                    val seek = pendingSeekMs
                    pendingSeekMs = 0L
                    if (seek > 0) {
                        runCatching {
                            MPVLib.command(arrayOf("seek", (seek / 1000.0).toString(), "absolute"))
                        }
                    }
                    publishState()
                }
                MPVLib.MPV_EVENT_END_FILE -> {
                    val stop = intentionalStop
                    intentionalStop = false
                    if (!stop) {
                        val eof = runCatching { MPVLib.getPropertyBoolean("eof-reached") }
                            .getOrNull() == true
                        if (!eof) {
                            _state.value = _state.value.copy(
                                isPlaying = false,
                                isBuffering = false,
                                error = PlayerError(
                                    message = "MPV no pudo reproducir este archivo.",
                                    canRetry = true
                                )
                            )
                        }
                    }
                    publishState()
                }
            }
        }
    }

    /** Conecta la superficie de vídeo (la UI la entrega desde un SurfaceView). */
    fun attachSurface(surface: Surface) {
        ensureInit()
        runCatching { MPVLib.attachSurface(surface) }
    }

    fun detachSurface() {
        runCatching { MPVLib.detachSurface() }
    }

    override fun prepare(fileId: String, mediaUrl: String, resumeMs: Long) {
        ensureInit()
        lastFileId = fileId
        lastMediaUrl = mediaUrl
        pendingSeekMs = resumeMs
        intentionalStop = false
        _state.value = PlaybackState(isBuffering = true)
        runCatching { MPVLib.command(arrayOf("loadfile", mediaUrl)) }
        startProgressLoop()
    }

    override fun play() {
        runCatching { MPVLib.setPropertyBoolean("pause", false) }
    }

    override fun pause() {
        runCatching { MPVLib.setPropertyBoolean("pause", true) }
    }

    override fun toggle() {
        runCatching { MPVLib.command(arrayOf("cycle", "pause")) }
        publishState()
    }

    override fun seekTo(positionMs: Long) {
        runCatching {
            MPVLib.command(
                arrayOf("seek", (positionMs.coerceAtLeast(0L) / 1000.0).toString(), "absolute")
            )
        }
        publishState()
    }

    override fun seekBy(deltaMs: Long) {
        runCatching {
            MPVLib.command(arrayOf("seek", (deltaMs / 1000.0).toString(), "relative"))
        }
        publishState()
    }

    override fun retry() {
        val url = lastMediaUrl ?: return
        prepare(lastFileId ?: "", url, _state.value.positionMs)
    }

    override fun audioTracks(): List<TrackInfo> = readTracks("audio")

    override fun subtitleTracks(): List<TrackInfo> = readTracks("sub")

    override fun selectAudio(track: TrackInfo) {
        runCatching { MPVLib.setPropertyString("aid", track.trackIndex.toString()) }
    }

    override fun selectSubtitle(track: TrackInfo) {
        runCatching { MPVLib.setPropertyString("sid", track.trackIndex.toString()) }
    }

    override fun disableSubtitles() {
        runCatching { MPVLib.setPropertyString("sid", "no") }
    }

    override fun cycleAspectRatio(): String {
        aspectIndex = (aspectIndex + 1) % 3
        return when (aspectIndex) {
            0 -> {
                runCatching {
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyDouble("video-zoom", 0.0)
                }
                "Ajustar"
            }
            1 -> {
                runCatching { MPVLib.setPropertyDouble("panscan", 1.0) }
                "Llenar"
            }
            else -> {
                runCatching {
                    MPVLib.setPropertyDouble("panscan", 1.0)
                    MPVLib.setPropertyDouble("video-zoom", 0.5)
                }
                "Zoom"
            }
        }
    }

    override fun release() {
        intentionalStop = true
        progressJob?.cancel()
        runCatching { MPVLib.command(arrayOf("stop")) }
        runCatching { MPVLib.detachSurface() }
        runCatching { MPVLib.removeObserver(eventObserver) }
        scope.cancel()
        // NOTA: no se llama a MPVLib.destroy(): la instancia se reutiliza
        // entre reproducciones para evitar ciclos create/destroy.
    }

    // ---------- interno ----------

    private fun ensureInit() {
        if (initialized) return
        runCatching {
            MPVLib.create(appContext)
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("hwdec", "mediacodec")
            MPVLib.setOptionString("sub-auto", "fuzzy")
            MPVLib.setOptionString("audio-display", "no")
            MPVLib.setOptionString("keep-open", "no")
            MPVLib.init()
            MPVLib.addObserver(eventObserver)
            initialized = true
        }
    }

    private fun readTracks(type: String): List<TrackInfo> {
        val count = runCatching { MPVLib.getPropertyInt("track-list/count") }.getOrNull()
            ?: return emptyList()
        val selectedId = runCatching {
            MPVLib.getPropertyInt(if (type == "audio") "aid" else "sid")
        }.getOrNull()
        return (0 until count).mapNotNull { i ->
            val t = runCatching { MPVLib.getPropertyString("track-list/$i/type") }.getOrNull()
            if (t != type) return@mapNotNull null
            val id = runCatching { MPVLib.getPropertyInt("track-list/$i/id") }.getOrNull()
                ?: return@mapNotNull null
            val lang = runCatching { MPVLib.getPropertyString("track-list/$i/lang") }.getOrNull()
            val title = runCatching { MPVLib.getPropertyString("track-list/$i/title") }.getOrNull()
            TrackInfo(
                groupIndex = 0,
                trackIndex = id,
                label = title?.takeIf { it.isNotBlank() } ?: "Pista $id",
                language = lang,
                isSelected = selectedId == id
            )
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                publishState()
                delay(500)
            }
        }
    }

    private fun publishState() {
        val pos = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull()
        val dur = runCatching { MPVLib.getPropertyDouble("duration") }.getOrNull()
        val paused = runCatching { MPVLib.getPropertyBoolean("pause") }.getOrNull()
        val caching = runCatching { MPVLib.getPropertyBoolean("paused-for-cache") }.getOrNull()
        val current = _state.value
        _state.value = current.copy(
            isPlaying = pos != null && paused == false,
            isBuffering = caching == true,
            positionMs = ((pos ?: 0.0) * 1000).toLong().coerceAtLeast(0L),
            durationMs = ((dur ?: 0.0) * 1000).toLong().coerceAtLeast(0L),
            error = current.error
        )
    }
}
