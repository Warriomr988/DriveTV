package com.panda.drivetv.data.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente mínimo de TMDB (v0.6.0 "Biblioteca"): HttpURLConnection + org.json,
 * mismo patrón que [com.panda.drivetv.data.remote.HttpDriveApi].
 *
 * Autenticación: API Key v3 como query param (forma oficial simple de TMDB).
 * La key la pone el usuario en Ajustes; nunca va hardcodeada ni en el repo.
 * Idioma es-MX para títulos/sinopsis en español.
 */
class TmdbApi {

    /** Géneros cacheados en memoria por proceso (una sola llamada por sesión). */
    private var genreCache: Map<Int, String>? = null
    private var tvGenreCache: Map<Int, String>? = null

    /**
     * Busca la película por título (y año si se conoce). Devuelve el primer
     * resultado o null si no hay coincidencias.
     *
     * Estrategia v0.6.1: si el título completo no da resultados, reintenta
     * recortando palabras por la derecha (por si quedó algún tag sin
     * limpiar, tipo "... AMNZ DL vip"); al final prueba sin año.
     */
    suspend fun searchMovie(apiKey: String, title: String, year: Int?): TmdbMovie? =
        withContext(Dispatchers.IO) {
            val words = title.split(" ").filter { it.isNotBlank() }
            val candidates = buildList {
                for (drop in 0..2) {
                    val q = words.dropLast(drop).joinToString(" ").trim()
                    if (q.isNotBlank() && (q.contains(" ") || words.size == 1)) add(q)
                }
                // v0.7.1: "1 Dr No" → probar también sin el número inicial.
                val stripped = title.replaceFirst(Regex("""^\d+\s+"""), "").trim()
                if (stripped.isNotBlank() && stripped != title) add(stripped)
            }.distinct()

            for (y in listOf(year, null).distinct()) {
                for (q in candidates) {
                    firstResult(apiKey, q, y)?.let { return@withContext it }
                }
            }
            null
        }

    private suspend fun firstResult(apiKey: String, query: String, year: Int?): TmdbMovie? {
        val params = mutableMapOf(
            "api_key" to apiKey,
            "language" to "es-MX",
            "query" to query,
            "include_adult" to "false"
        )
        if (year != null) params["year"] = year.toString()
        val json = get("$BASE/search/movie", params)
        val results = json.optJSONArray("results") ?: return null
        return if (results.length() > 0) results.getJSONObject(0).toTmdbMovie() else null
    }

    /** Mapa id → nombre de género en español (se pide una vez por proceso). */
    suspend fun genreNames(apiKey: String): Map<Int, String> =
        genreNamesFor(apiKey, tv = false)

    /** Géneros de series (TMDB usa lista separada para TV, v0.7.0). */
    suspend fun tvGenreNames(apiKey: String): Map<Int, String> =
        genreNamesFor(apiKey, tv = true)

    private suspend fun genreNamesFor(apiKey: String, tv: Boolean): Map<Int, String> =
        withContext(Dispatchers.IO) {
            val cached = if (tv) tvGenreCache else genreCache
            cached ?: run {
                val json = get(
                    "$BASE/genre/${if (tv) "tv" else "movie"}/list",
                    mapOf("api_key" to apiKey, "language" to "es-MX")
                )
                val map = mutableMapOf<Int, String>()
                val arr = json.optJSONArray("genres")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val g = arr.getJSONObject(i)
                        map[g.getInt("id")] = g.optString("name", "")
                    }
                }
                if (tv) tvGenreCache = map else genreCache = map
                map
            }
        }

    /**
     * Busca serie por título (v0.7.0). Se usa cuando /search/movie no da
     * resultados: muchos archivos sueltos son series.
     */
    suspend fun searchTv(apiKey: String, title: String, year: Int?): TmdbMovie? =
        withContext(Dispatchers.IO) {
            val words = title.split(" ").filter { it.isNotBlank() }
            val candidates = buildList {
                for (drop in 0..2) {
                    val q = words.dropLast(drop).joinToString(" ").trim()
                    if (q.isNotBlank() && (q.contains(" ") || words.size == 1)) add(q)
                }
                // v0.7.1: igual que en películas, sin el número inicial.
                val stripped = title.replaceFirst(Regex("""^\d+\s+"""), "").trim()
                if (stripped.isNotBlank() && stripped != title) add(stripped)
            }.distinct()

            for (y in listOf(year, null).distinct()) {
                for (q in candidates) {
                    firstTvResult(apiKey, q, y)?.let { return@withContext it }
                }
            }
            null
        }

    private suspend fun firstTvResult(apiKey: String, query: String, year: Int?): TmdbMovie? {
        val params = mutableMapOf(
            "api_key" to apiKey,
            "language" to "es-MX",
            "query" to query,
            "include_adult" to "false"
        )
        if (year != null) params["first_air_date_year"] = year.toString()
        val json = get("$BASE/search/tv", params)
        val results = json.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        return results.getJSONObject(0).toTmdbTv()
    }

    private fun JSONObject.toTmdbTv(): TmdbMovie {
        val date = optString("first_air_date", "")
        val year = date.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
        val ids = optJSONArray("genre_ids")
        val genreIds = buildList {
            if (ids != null) for (i in 0 until ids.length()) add(ids.optInt(i))
        }
        return TmdbMovie(
            id = getLong("id"),
            title = optString("name", ""),
            year = year,
            overview = optString("overview", ""),
            genreIds = genreIds,
            posterPath = optString("poster_path", null),
            backdropPath = optString("backdrop_path", null),
            mediaType = "tv"
        )
    }

    fun posterUrl(path: String?, size: String = "w342"): String? =
        path?.let { "$IMG_BASE/$size$it" }

    fun backdropUrl(path: String?, size: String = "w780"): String? =
        path?.let { "$IMG_BASE/$size$it" }

    // ---------- HTTP interno ----------

    /**
     * Pacing v0.7.2: TMDB permite ~40 peticiones/10 s. Con colecciones
     * grandes el escaneo inicial saturaba el límite (429) y las películas
     * quedaban sin póster. 350 ms entre llamadas ≈ 28/10 s, con margen.
     */
    private suspend fun get(baseUrl: String, params: Map<String, String>): JSONObject {
        delay(REQUEST_PACING_MS)
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val conn = (URL("$baseUrl?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } else {
                throw TmdbException(code, "Error de TMDB ($code).")
            }
        } catch (e: TmdbException) {
            throw e
        } catch (e: IOException) {
            throw TmdbException(-1, "Sin conexión de red.")
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.toTmdbMovie(): TmdbMovie {
        val date = optString("release_date", "")
        val year = date.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
        val ids = optJSONArray("genre_ids")
        val genreIds = buildList {
            if (ids != null) for (i in 0 until ids.length()) add(ids.optInt(i))
        }
        return TmdbMovie(
            id = getLong("id"),
            title = optString("title", ""),
            year = year,
            overview = optString("overview", ""),
            genreIds = genreIds,
            posterPath = optString("poster_path", null),
            backdropPath = optString("backdrop_path", null)
        )
    }

    companion object {
        private const val BASE = "https://api.themoviedb.org/3"
        private const val IMG_BASE = "https://image.tmdb.org/t/p"
        private const val REQUEST_PACING_MS = 350L
    }
}

/** Error de TMDB con código HTTP (-1 = sin red). */
class TmdbException(val code: Int, message: String) : IOException(message)
