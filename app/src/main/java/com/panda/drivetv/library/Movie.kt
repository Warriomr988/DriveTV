package com.panda.drivetv.library

/**
 * Una versión/archivo de la película (v0.7.1): la misma película suele
 * estar en varias calidades (4K Remux, 1080p BluRay, 720p WEB-DL…).
 */
data class MovieVersion(
    val driveId: String,
    val fileName: String,
    /** Etiqueta visible: "4K Remux", "1080p BluRay"… */
    val label: String
)

/** Película o serie de la biblioteca (v0.6.0 "Biblioteca"). */
data class Movie(
    val driveId: String,
    val fileName: String,
    val title: String,
    val year: Int?,
    val overview: String,
    val genres: List<String>,
    val posterUrl: String?,
    val backdropUrl: String?,
    /** true si TMDB lo identificó como serie (v0.7.0). */
    val isSeries: Boolean = false,
    /**
     * Versiones del mismo título ordenadas de mejor a peor calidad
     * (v0.7.1). [driveId]/[fileName] son la mejor versión.
     */
    val versions: List<MovieVersion> = emptyList(),
    /** true si alguna versión es Dolby Vision (v0.7.3). */
    val dolbyVision: Boolean = false
) {
    /** Todas las versiones; si no hay lista, la única conocida. */
    fun allVersions(): List<MovieVersion> =
        versions.ifEmpty { listOf(MovieVersion(driveId, fileName, "Archivo")) }
}
