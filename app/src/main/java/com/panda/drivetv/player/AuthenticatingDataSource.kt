package com.panda.drivetv.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * DataSource que inyecta `Authorization: Bearer <token>` en cada petición
 * HTTP hacia Drive (etapa 5 de la guía).
 *
 * - El token se pide fresco en cada `open()` vía [tokenProvider].
 * - Soporta Range/seek: Media3 reabre el DataSource con otro DataSpec y el
 *   header se vuelve a inyectar, así el seek funciona sin descarga completa.
 * - Un 401 se propaga como error al reproductor; la política de
 *   refresh+reintento vive en [Media3Player]/UI (tabla de errores: 401 →
 *   refrescar token y reintentar una vez).
 */
class AuthenticatingDataSource(
    private val upstream: DataSource,
    private val tokenProvider: () -> String?
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val token = tokenProvider()
        val spec = if (!token.isNullOrBlank()) {
            dataSpec.buildUpon()
                .setHttpRequestHeaders(
                    dataSpec.httpRequestHeaders + ("Authorization" to "Bearer $token")
                )
                .build()
        } else {
            dataSpec
        }
        return upstream.open(spec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri() = upstream.uri

    override fun close() {
        upstream.close()
    }
}
