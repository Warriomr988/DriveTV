package com.panda.drivetv.player

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors

/**
 * Mini servidor HTTP local (127.0.0.1) que re-transmite un archivo de Drive
 * añadiendo el `Authorization: Bearer` del lado de DriveTV.
 *
 * Player VIP no puede poner headers HTTP, así que DriveTV le entrega una URL
 * local sin autenticación:
 * `http://127.0.0.1:PUERTO/vip/<fileId>/<nombre>`.
 *
 * Soporta `Range` (seek): reenvía el rango tal cual a Drive y devuelve 206
 * con sus headers; sin rango devuelve 200. El cuerpo se re-transmite por
 * streaming, sin guardar nada en disco.
 */
class VipProxyServer(
    private val tokenProvider: () -> String?,
    private val mediaUrl: (String) -> String
) {
    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r ->
        Thread(r, "vip-proxy").apply { isDaemon = true }
    }

    val port: Int get() = socket?.localPort ?: -1
    val isRunning: Boolean get() = socket?.let { !it.isClosed } == true

    @Synchronized
    fun start(): Int {
        if (!isRunning) {
            socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
            val s = socket!!
            Thread({
                while (!s.isClosed) {
                    try {
                        val client = s.accept()
                        pool.execute { handle(client) }
                    } catch (_: Exception) {
                        // socket cerrado: salir del loop
                    }
                }
            }, "vip-proxy-accept").apply { isDaemon = true; start() }
        }
        return port
    }

    @Synchronized
    fun stop() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 30_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                writeStatus(client.getOutputStream(), 405, "Method Not Allowed")
                return
            }
            val segs = parts[1].substringBefore("?").trim('/').split("/")
            if (segs.size < 3 || segs[0] != "vip") {
                writeStatus(client.getOutputStream(), 404, "Not Found")
                return
            }
            val fileId = segs[1]
            var range: String? = null
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.startsWith("Range:", ignoreCase = true)) {
                    range = line!!.substringAfter(":").trim()
                }
            }
            proxy(fileId, range, client.getOutputStream())
        } catch (_: Exception) {
            // conexión caída: nada que hacer
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun proxy(fileId: String, range: String?, out: OutputStream) {
        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            writeStatus(out, 401, "Unauthorized")
            return
        }
        val conn = (URL(mediaUrl(fileId)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            if (range != null) setRequestProperty("Range", range)
        }
        try {
            val code = conn.responseCode
            val reason = when (code) {
                200 -> "OK"
                206 -> "Partial Content"
                else -> "Error"
            }
            val head = StringBuilder("HTTP/1.1 $code $reason\r\n")
            header(conn, "Content-Length")?.let { head.append("Content-Length: $it\r\n") }
            header(conn, "Content-Range")?.let { head.append("Content-Range: $it\r\n") }
            header(conn, "Content-Type")?.let { head.append("Content-Type: $it\r\n") }
            head.append("Accept-Ranges: bytes\r\n")
            head.append("Connection: close\r\n\r\n")
            out.write(head.toString().toByteArray(Charsets.US_ASCII))
            out.flush()
            if (code == 200 || code == 206) {
                conn.inputStream.use { it.copyTo(out) }
            }
            out.flush()
        } catch (_: Exception) {
            // Drive cortó o el cliente se fue: nada que hacer
        } finally {
            conn.disconnect()
        }
    }

    private fun header(conn: HttpURLConnection, name: String): String? =
        conn.headerFields.entries
            .firstOrNull { it.key?.equals(name, ignoreCase = true) == true }
            ?.value?.firstOrNull()

    private fun writeStatus(out: OutputStream, code: Int, text: String) {
        out.write(
            "HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.US_ASCII)
        )
        out.flush()
    }
}
