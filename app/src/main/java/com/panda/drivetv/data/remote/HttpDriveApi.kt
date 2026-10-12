package com.panda.drivetv.data.remote

import com.panda.drivetv.data.model.DriveFile
import com.panda.drivetv.data.model.SharedDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Implementación de [DriveApi] con HttpURLConnection + org.json (sin
 * dependencias extra): Drive API v3, solo lectura.
 *
 * - El Bearer se añade en cada petición desde [tokenProvider].
 * - El token nunca se escribe en logs ni se guarda en los modelos.
 * - Los errores HTTP se traducen a [DriveApiException] con su código
 *   (401/403/404/429/5xx) para que la UI muestre el mensaje de la guía.
 */
class HttpDriveApi(
    private val tokenProvider: () -> String?
) : DriveApi {

    override suspend fun listFiles(parentId: String): List<DriveFile> =
        withContext(Dispatchers.IO) {
            queryFiles(
                q = "'$parentId' in parents and trashed=false",
                orderBy = "folder,name"
            )
        }

    override suspend fun listAllVideos(): List<DriveFile> =
        withContext(Dispatchers.IO) {
            queryFiles(
                q = "mimeType contains 'video/' and trashed=false",
                orderBy = "name",
                // v0.7.0: incluye también los vídeos de unidades compartidas.
                extraParams = mapOf(
                    "corpora" to "allDrives",
                    "includeItemsFromAllDrives" to "true",
                    "supportsAllDrives" to "true"
                )
            )
        }

    override suspend fun listSharedDrives(): List<SharedDrive> =
        withContext(Dispatchers.IO) {
            val json = get(
                "https://www.googleapis.com/drive/v3/drives",
                mapOf("fields" to "drives(id,name)", "pageSize" to "100")
            )
            val arr = json.optJSONArray("drives") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    add(SharedDrive(id = d.getString("id"), name = d.optString("name", "Sin nombre")))
                }
            }
        }

    private fun queryFiles(
        q: String,
        orderBy: String,
        extraParams: Map<String, String> = emptyMap()
    ): List<DriveFile> {
        // v0.6.0: paginación real — la biblioteca necesita TODOS los vídeos,
        // no solo los primeros 100. Tope de 20 páginas (2000 archivos).
        val all = mutableListOf<DriveFile>()
        var pageToken: String? = null
        var pages = 0
        do {
            val params = mutableMapOf(
                "q" to q,
                "fields" to "files(id,name,mimeType,size,parents),nextPageToken",
                "pageSize" to "100",
                "orderBy" to orderBy,
                // v0.7.0: ver también el contenido de unidades compartidas.
                "supportsAllDrives" to "true",
                "includeItemsFromAllDrives" to "true"
            )
            params.putAll(extraParams)
            if (pageToken != null) params["pageToken"] = pageToken
            val json = get("https://www.googleapis.com/drive/v3/files", params)
            val files = json.getJSONArray("files")
            for (i in 0 until files.length()) {
                all.add(files.getJSONObject(i).toDriveFile())
            }
            pageToken = json.optString("nextPageToken", null)?.takeIf { it.isNotBlank() }
            pages++
        } while (pageToken != null && pages < 20)
        return all
    }

    override suspend fun getFile(fileId: String): DriveFile =
        withContext(Dispatchers.IO) {
            val json = get(
                "https://www.googleapis.com/drive/v3/files/$fileId",
                mapOf("fields" to "id,name,mimeType,size,parents")
            )
            json.toDriveFile()
        }

    override fun mediaUrl(fileId: String): String =
        "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"

    // ---------- HTTP interno ----------

    private fun get(baseUrl: String, params: Map<String, String>): JSONObject {
        val token = tokenProvider()
            ?: throw DriveApiException(401, "Sin sesión: no hay access token.")
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val conn = (URL("$baseUrl?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } else {
                // Se lee el cuerpo del error pero SIN el token (el token solo
                // viaja en el header Authorization, nunca en la URL).
                val body = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                throw DriveApiException(code, mapError(code, body))
            }
        } catch (e: DriveApiException) {
            throw e
        } catch (e: IOException) {
            throw DriveApiException(-1, "Sin conexión de red.")
        } finally {
            conn.disconnect()
        }
    }

    private fun mapError(code: Int, body: String?): String {
        val detail = runCatching {
            body?.let { JSONObject(it).optJSONObject("error")?.optString("message") }
        }.getOrNull().orEmpty()
        return when (code) {
            401 -> "Sesión vencida o inválida."
            403 -> "Sin permiso para este archivo o cuota excedida."
            404 -> "Archivo no encontrado en Drive."
            429 -> "Demasiadas peticiones a Drive; espera un momento."
            in 500..599 -> "Error temporal de Google Drive."
            else -> "Error de Drive ($code). $detail".trim()
        }
    }

    private fun JSONObject.toDriveFile(): DriveFile {
        val parents = optJSONArray("parents")
        return DriveFile(
            id = getString("id"),
            name = getString("name"),
            mimeType = optString("mimeType", "application/octet-stream"),
            size = optString("size", null)?.toLongOrNull(),
            parentId = if (parents != null && parents.length() > 0) parents.getString(0) else null
        )
    }
}
