package com.panda.drivetv.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.panda.drivetv.DriveTVApp
import java.net.URLEncoder

/**
 * Puente DriveTV → Player VIP (clon parcheado del reproductor del TV).
 *
 * Player VIP no puede autenticarse contra Drive, así que DriveTV levanta un
 * proxy local ([VipProxyServer]) y le entrega una URL `http://127.0.0.1:...`
 * sin auth. El proxy añade el Bearer y re-transmite los bytes con soporte
 * de Range (seek).
 *
 * Uso: [playOnVip]. Si Player VIP no está instalado, avisa con un Toast y
 * devuelve false para que la UI no haga nada más.
 */
object VipBridge {
    const val VIP_PACKAGE = "com.panda.smartplayer"
    const val VIP_ACTIVITY = "com.panda.smartplayer.activity.VideoPlayerActivity"

    private var server: VipProxyServer? = null

    @Synchronized
    fun ensureStarted(app: DriveTVApp): Int {
        var s = server
        if (s == null || !s.isRunning) {
            s = VipProxyServer(
                tokenProvider = { app.authManager.accessToken() },
                mediaUrl = { fileId -> app.driveRepository.mediaUrl(fileId) }
            )
            server = s
        }
        return s.start()
    }

    /**
     * URL del proxy local para [fileId], o null si no se pudo iniciar.
     * El proxy inyecta el Bearer <redacted> y soporta Range/seek (v0.8.0: también
     * lo usa el reproductor MPV interno).
     */
    fun proxyUrl(app: DriveTVApp, fileId: String, fileName: String): String? {
        val port = try {
            ensureStarted(app)
        } catch (_: Exception) {
            -1
        }
        if (port <= 0) return null
        val safeName = URLEncoder.encode(fileName, "UTF-8")
        return "http://127.0.0.1:$port/vip/$fileId/$safeName"
    }

    /**
     * Abre [fileId] en Player VIP. Devuelve true si se lanzó el reproductor.
     */
    fun playOnVip(context: Context, app: DriveTVApp, fileId: String, fileName: String): Boolean {
        val url = proxyUrl(app, fileId, fileName)
        if (url == null) {
            Toast.makeText(context, "No se pudo iniciar el puente local", Toast.LENGTH_LONG).show()
            return false
        }
        val uri = Uri.parse(url)

        // 1) Intent explícito a la actividad parcheada de Player VIP.
        val explicit = Intent(Intent.ACTION_VIEW).apply {
            setClassName(VIP_PACKAGE, VIP_ACTIVITY)
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(explicit)
            return true
        } catch (_: ActivityNotFoundException) {
            // 2) Plan B: intent implícito (Player VIP declara http/https en su filtro).
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return true
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "Instala Player VIP en el TV primero", Toast.LENGTH_LONG).show()
                return false
            }
        }
    }

    /** Detiene el proxy local (al salir de la app, por ejemplo). */
    fun stop() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
    }
}
