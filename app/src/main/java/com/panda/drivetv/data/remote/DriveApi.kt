package com.panda.drivetv.data.remote

import com.panda.drivetv.data.model.DriveFile
import com.panda.drivetv.data.model.SharedDrive

/**
 * Frontera de la API de Drive (etapa 3 de la guía).
 */
interface DriveApi {
    suspend fun listFiles(parentId: String): List<DriveFile>
    suspend fun getFile(fileId: String): DriveFile
    /** Todos los vídeos del Drive (búsqueda por mimeType), para la portada. */
    suspend fun listAllVideos(): List<DriveFile>
    /** Unidades compartidas visibles para el usuario (v0.7.0). */
    suspend fun listSharedDrives(): List<SharedDrive>
    fun mediaUrl(fileId: String): String
}

/** Error tipado de la API de Drive para mapear mensajes de la guía (tabla de errores). */
class DriveApiException(
    val httpCode: Int,
    message: String
) : Exception(message)
