package com.panda.drivetv.data.repository

import com.panda.drivetv.data.model.DriveFile
import com.panda.drivetv.data.model.SharedDrive
import com.panda.drivetv.data.remote.DriveApi

/**
 * Repositorio: la UI solo habla con esta clase (etapa 3 de la guía).
 * Aquí irían caché, paginación adicional y reintentos con backoff.
 */
class DriveRepository(
    private val api: DriveApi
) {
    suspend fun listFolder(parentId: String): List<DriveFile> =
        api.listFiles(parentId)

    /** Todos los vídeos del Drive (para la portada). */
    suspend fun listAllVideos(): List<DriveFile> =
        api.listAllVideos()

    /** Unidades compartidas (v0.7.0). */
    suspend fun listSharedDrives(): List<SharedDrive> =
        api.listSharedDrives()

    suspend fun getFile(fileId: String): DriveFile =
        api.getFile(fileId)

    fun mediaUrl(fileId: String): String =
        api.mediaUrl(fileId)
}
