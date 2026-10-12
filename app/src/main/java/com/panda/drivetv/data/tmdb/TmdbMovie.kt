package com.panda.drivetv.data.tmdb

/**
 * Película según TMDB (v0.6.0 "Biblioteca").
 *
 * Solo se guardan posterPath/backdropPath (rutas relativas); la URL completa
 * se construye con [TmdbApi.posterUrl]/[TmdbApi.backdropUrl] para no atar el
 * caché a un tamaño de imagen concreto.
 */
data class TmdbMovie(
    val id: Long,
    val title: String,
    val year: Int?,
    val overview: String,
    val genreIds: List<Int>,
    val posterPath: String?,
    val backdropPath: String?,
    /** "movie" o "tv" (v0.7.0: las series también tienen carátula). */
    val mediaType: String = "movie"
)
