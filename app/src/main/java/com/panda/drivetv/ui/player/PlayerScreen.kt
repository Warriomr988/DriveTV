package com.panda.drivetv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.panda.drivetv.DriveTVApp
import com.panda.drivetv.R
import com.panda.drivetv.player.Media3Player
import com.panda.drivetv.player.MpvPlayer
import com.panda.drivetv.player.PlayerManager
import com.panda.drivetv.player.TrackInfo
import com.panda.drivetv.player.VipBridge
import com.panda.drivetv.ui.Screen
import com.panda.drivetv.ui.formatTime
import com.panda.drivetv.ui.tv.TvButton
import kotlinx.coroutines.delay

private enum class Panel { AUDIO, SUBS }

/**
 * Reproductor de TV (etapas 5 y 6 de la guía).
 *
 * Controles D-pad:
 * - Controles ocultos: OK = play/pausa · ←/→ = ∓10 s · ↑/↓ = mostrar controles
 * - Controles visibles: el D-pad navega entre los botones (borde blanco) y
 *   OK activa el botón enfocado; las flechas NO adelantan el vídeo.
 * - Back: cierra el panel de pistas → oculta los controles → sale del vídeo
 *
 * El vídeo se reproduce por streaming progresivo desde Drive (Range/seek) con
 * Bearer inyectado por [com.panda.drivetv.player.AuthenticatingDataSource].
 */
@Composable
fun PlayerScreen(
    fileId: String,
    fileName: String,
    app: DriveTVApp,
    onExit: () -> Unit,
    onNeedSignIn: () -> Unit,
    engine: Screen.PlayerEngine = Screen.PlayerEngine.MEDIA3
) {
    val context = LocalContext.current
    // v0.8.0: motor elegible (Media3 o MPV/libmpv); la UI habla por la
    // interfaz PlayerManager en ambos casos.
    val player: PlayerManager = remember {
        when (engine) {
            Screen.PlayerEngine.MEDIA3 -> Media3Player(context, app.authManager)
            Screen.PlayerEngine.MPV -> MpvPlayer(context)
        }
    }
    val state by player.state.collectAsState()

    // v0.8.1: mantener la pantalla encendida mientras se reproduce, para que
    // el modo ambiente del TV no interrumpa la película. Al pausar se libera
    // (ahí sí puede entrar el ambiente) y al salir se limpia siempre.
    val activity = context as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(state.isPlaying) {
        val window = activity?.window
        if (state.isPlaying) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableStateOf(0) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var resumeNote by remember { mutableStateOf(false) }
    var aspectLabel by remember { mutableStateOf("Ajustar") }
    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }

    fun showControls() {
        controlsVisible = true
        interactions++
    }

    // Preparar una sola vez por fileId.
    // v0.8.0: MPV reproduce vía el proxy local (inyecta el Bearer <redacted> con
    // Range/seek); Media3 usa la URL directa de Drive con su DataSource.
    LaunchedEffect(fileId) {
        val resume = app.resumeManager.get(fileId)
        if (resume > 0) {
            resumeNote = true
            delay(3000)
            resumeNote = false
        }
        val mediaUrl = if (engine == Screen.PlayerEngine.MPV) {
            VipBridge.proxyUrl(app, fileId, fileName)
                ?: app.driveRepository.mediaUrl(fileId)
        } else {
            app.driveRepository.mediaUrl(fileId)
        }
        player.prepare(fileId, mediaUrl, resume)
    }

    // Guardar posición al salir y liberar.
    DisposableEffect(fileId) {
        onDispose {
            val s = player.state.value
            if (s.positionMs > 0 && s.error == null) {
                app.resumeManager.save(fileId, s.positionMs, s.durationMs)
            }
            player.release()
        }
    }

    // Al mostrarse los controles, el foco va al botón play/pausa para que el
    // D-pad navegue entre los botones (con su borde blanco).
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) playPauseFocus.requestFocus()
    }

    // Auto-ocultar controles mientras se reproduce.
    LaunchedEffect(controlsVisible, interactions, state.isPlaying) {
        if (controlsVisible && state.isPlaying && state.error == null) {
            delay(4000)
            controlsVisible = false
            rootFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Con el panel de pistas abierto, el D-pad navega el panel
                // (no debe adelantar/atrasar el vídeo ni pausarlo).
                if (panel != null) {
                    return@onPreviewKeyEvent if (event.key == Key.Back) {
                        panel = null
                        playPauseFocus.requestFocus()
                        showControls()
                        true
                    } else {
                        false
                    }
                }
                // Con los controles visibles, el D-pad navega entre los botones:
                // no se consumen las flechas ni el centro, para que el foco se
                // mueva (borde blanco) y OK active el botón enfocado.
                if (controlsVisible) {
                    return@onPreviewKeyEvent if (event.key == Key.Back) {
                        controlsVisible = false
                        rootFocus.requestFocus()
                        true
                    } else {
                        false
                    }
                }
                // Controles ocultos: atajos directos sobre el vídeo.
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        player.toggle(); showControls(); true
                    }
                    Key.DirectionLeft -> {
                        player.seekBy(-10_000); showControls(); true
                    }
                    Key.DirectionRight -> {
                        player.seekBy(10_000); showControls(); true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        showControls(); true
                    }
                    Key.Back -> {
                        onExit(); true
                    }
                    else -> false
                }
            }
    ) {
        // v0.8.0: superficie según el motor (PlayerView para Media3,
        // SurfaceView + attachSurface para MPV).
        AndroidView(
            factory = { ctx ->
                when (val p = player) {
                    is Media3Player -> PlayerView(ctx).also { p.bindView(it) }
                    is MpvPlayer -> android.view.SurfaceView(ctx).also { sv ->
                        sv.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                p.attachSurface(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: android.view.SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) = Unit

                            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                                p.detachSurface()
                            }
                        })
                    }

                    else -> PlayerView(ctx)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering
        if (state.isBuffering && state.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.error_buffering))
                }
            }
        }

        // Aviso de reanudación
        if (resumeNote) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    stringResource(R.string.player_resume),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(12.dp)
                )
            }
        }

        // Controles
        if (controlsVisible && state.error == null) {
            ControlsOverlay(
                title = fileName,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                aspectLabel = aspectLabel,
                playPauseFocus = playPauseFocus,
                onPlayPause = { player.toggle(); showControls() },
                onSeek = { player.seekTo(it); showControls() },
                onAudio = { panel = Panel.AUDIO; showControls() },
                onSubs = { panel = Panel.SUBS; showControls() },
                onAspect = { aspectLabel = player.cycleAspectRatio(); showControls() },
                onVip = {
                    player.pause()
                    showControls()
                    VipBridge.playOnVip(context, app, fileId, fileName)
                },
                onClose = { onExit() }
            )
        }

        // Panel de pistas
        panel?.let { p ->
            TrackPanel(
                title = if (p == Panel.AUDIO) "Pista de audio" else "Subtítulos",
                tracks = if (p == Panel.AUDIO) player.audioTracks() else player.subtitleTracks(),
                showDisableSubs = p == Panel.SUBS,
                onSelect = { t ->
                    if (p == Panel.AUDIO) player.selectAudio(t) else player.selectSubtitle(t)
                    panel = null
                    playPauseFocus.requestFocus()
                    showControls()
                },
                onDisableSubs = {
                    player.disableSubtitles()
                    panel = null
                    playPauseFocus.requestFocus()
                    showControls()
                },
                onDismiss = {
                    panel = null
                    playPauseFocus.requestFocus()
                    showControls()
                }
            )
        }

        // Error
        state.error?.let { err ->
            val errorFocus = remember { FocusRequester() }
            LaunchedEffect(err) { errorFocus.requestFocus() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(48.dp)
                ) {
                    Text("⚠️", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(err.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (err.canRetry) {
                            TvButton(
                                onClick = { player.retry(); showControls() },
                                modifier = Modifier.focusRequester(errorFocus)
                            ) {
                                Text("Reintentar")
                            }
                        }
                        if (err.isAuthError) {
                            TvButton(onClick = {
                                app.authManager.signOut()
                                onNeedSignIn()
                            }) {
                                Text("Iniciar sesión de nuevo")
                            }
                        }
                        // v0.7.3: si el decodificador falló (p. ej. Dolby
                        // Vision), ofrecer Player VIP directamente.
                        if (err.isDecoderError) {
                            TvButton(onClick = {
                                VipBridge.playOnVip(context, app, fileId, fileName)
                            }) {
                                Text("📺 Probar en Player VIP")
                            }
                        }
                        TvButton(onClick = { onExit() }) {
                            Text("Salir")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlsOverlay(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    aspectLabel: String,
    playPauseFocus: FocusRequester,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onAudio: () -> Unit,
    onSubs: () -> Unit,
    onAspect: () -> Unit,
    onVip: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // Título superior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                .padding(24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.weight(1f))

        // Barra inferior
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f))))
                .padding(24.dp)
        ) {
            // Progreso
            val frac = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac.coerceIn(0f, 1f))
                        .background(Color.White)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(positionMs), style = MaterialTheme.typography.bodyMedium)
                Text(formatTime(durationMs), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    onClick = onPlayPause,
                    modifier = Modifier.focusRequester(playPauseFocus)
                ) {
                    Text(if (isPlaying) "⏸ Pausa" else "▶ Reproducir")
                }
                TvButton(onClick = { onSeek(positionMs - 30_000) }) {
                    Text("⏪ 30s")
                }
                TvButton(onClick = { onSeek(positionMs + 30_000) }) {
                    Text("30s ⏩")
                }
                Spacer(Modifier.weight(1f))
                TvButton(onClick = onAspect) { Text("🖥 $aspectLabel") }
                TvButton(onClick = onVip) { Text("📺 Player VIP") }
                TvButton(onClick = onAudio) { Text("🔊 Audio") }
                TvButton(onClick = onSubs) { Text("💬 Subtítulos") }
                TvButton(onClick = onClose) { Text("✕ Salir") }
            }
        }
    }
}

@Composable
private fun TrackPanel(
    title: String,
    tracks: List<TrackInfo>,
    showDisableSubs: Boolean,
    onSelect: (TrackInfo) -> Unit,
    onDisableSubs: () -> Unit,
    onDismiss: () -> Unit
) {
    // Al abrir el panel, el foco va al primer botón para navegar con D-pad.
    val panelFocus = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .background(Color(0xFF1B1B1B))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            if (tracks.isEmpty()) {
                Text("No hay pistas disponibles.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(tracks, key = { _, t -> "${t.groupIndex}-${t.trackIndex}" }) { index, t ->
                        TvButton(
                            onClick = { onSelect(t) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(panelFocus) else Modifier)
                        ) {
                            Text((if (t.isSelected) "✓ " else "") + t.label)
                        }
                    }
                }
            }
            if (showDisableSubs) {
                Spacer(Modifier.height(8.dp))
                TvButton(
                    onClick = onDisableSubs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (tracks.isEmpty()) Modifier.focusRequester(panelFocus) else Modifier)
                ) {
                    Text("Desactivar subtítulos")
                }
            }
            Spacer(Modifier.height(8.dp))
            TvButton(
                onClick = onDismiss,
                modifier = Modifier.then(
                    if (tracks.isEmpty() && !showDisableSubs) Modifier.focusRequester(panelFocus) else Modifier
                )
            ) { Text("Cerrar") }
        }
    }
    LaunchedEffect(Unit) { panelFocus.requestFocus() }
}
