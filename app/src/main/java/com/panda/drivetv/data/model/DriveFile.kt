package com.panda.drivetv.data.model

/**
 * Modelo mínimo de archivo de Drive (etapa 3 de la guía).
 * Regla: el access token NUNCA se guarda dentro de este modelo.
 */
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val parentId: String?
) {
    val isFolder: Boolean
        get() = mimeType == "application/vnd.google-apps.folder"

    val isVideo: Boolean
        get() = mimeType.startsWith("video/")
}
