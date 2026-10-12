package com.panda.drivetv.library

import android.content.Context
import com.panda.drivetv.data.model.DriveFile
import com.panda.drivetv.data.repository.DriveRepository
import com.panda.drivetv.data.tmdb.TmdbApi
import com.panda.drivetv.data.tmdb.TmdbException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Biblioteca de películas (v0.6.0):
 * 1. Lista todos los vídeos del Drive (cualquier carpeta).
 * 2. Limpia cada nombre con [FilenameParser].
 * 3. Busca metadatos en TMDB (si hay API key).
 * 4. Cachea todo en un JSON local: los archivos sin cambios no vuelven a
 *    tocar la red; las carátulas las cachea Coil en disco.
 *
 * v0.7.1: los archivos se AGRUPAN por (título, año) antes de buscar en
 * TMDB — la misma película en 4K/1080p/720p genera UNA sola búsqueda y
 * una sola tarjeta, con selector de versión. Esto reduce las peticiones
 * a TMDB de miles a cientos en colecciones grandes.
 *
 * El progreso se reporta como (procesadas, total) para la barra de carga.
 */
class MovieRepository(
    private val context: Context,
    private val driveRepository: DriveRepository,
    private val tmdb: TmdbApi
) {

    /**
     * Un grupo de archivos que son la misma película (o el mismo episodio
     * de una serie): una sola búsqueda TMDB y una sola tarjeta.
     */
    private data class Group(
        val title: String,
        val year: Int?,
        val episode: String?,
        val files: List<Pair<DriveFile, FilenameParser.Parsed>>
    )

    /**
     * Carga la biblioteca. Si [forceRefresh] es false y hay caché, los
     * grupos sin cambios no consultan TMDB de nuevo.
     */
    suspend fun loadLibrary(
        apiKey: String?,
        forceRefresh: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Movie> {
        val videos = driveRepository.listAllVideos()
        val parsed = videos.map { it to FilenameParser.parse(it.name) }
        val groups = groupByTitle(parsed)
        val cache = if (forceRefresh) mutableMapOf() else readCache().toMutableMap()
        val total = groups.size
        var done = 0

        val movies = groups.map { group ->
            done++
            onProgress(done, total)
            val key = groupKey(group)
            val cached = cache[key]
            if (cached != null && cacheMatches(cached, group)) {
                cached
            } else {
                val (movie, cacheable) = buildMovie(group, apiKey)
                // v0.7.2: si TMDB nos limitó (429), NO se cachea: en la
                // próxima apertura se reintenta en vez de quedar sin póster.
                if (cacheable) cache[key] = movie
                movie
            }
        }

        writeCache(cache)
        return movies.sortedBy { it.title.lowercase() }
    }

    /**
     * Agrupa archivos por (título, episodio). Si el mismo título aparece
     * con años distintos (remakes), se separa por año; los sin año se
     * juntan con el año más común del título.
     */
    private fun groupByTitle(
        parsed: List<Pair<DriveFile, FilenameParser.Parsed>>
    ): List<Group> {
        val byTitleEp = parsed.groupBy {
            it.second.title.lowercase() to it.second.episode
        }
        val groups = mutableListOf<Group>()
        for ((_, items) in byTitleEp) {
            val first = items.first().second
            val years = items.mapNotNull { it.second.year }.toSet()
            if (years.size <= 1) {
                groups.add(Group(first.title, years.firstOrNull(), first.episode, items))
            } else {
                val mainYear = years.maxByOrNull { y -> items.count { it.second.year == y } }
                val (noYear, withYear) = items.partition { it.second.year == null }
                for ((y, fs) in withYear.groupBy { it.second.year }) {
                    groups.add(
                        Group(
                            first.title, y, first.episode,
                            if (y == mainYear) fs + noYear else fs
                        )
                    )
                }
            }
        }
        return groups
    }

    private fun groupKey(group: Group): String =
        "${group.title.lowercase()}|${group.year ?: 0}|${group.episode ?: ""}"

    /** El caché vale si son los mismos archivos con los mismos nombres. */
    private fun cacheMatches(cached: Movie, group: Group): Boolean {
        val ids = group.files.map { it.first.id }.toSet()
        val cachedIds = cached.allVersions().map { it.driveId }.toSet()
        if (ids != cachedIds) return false
        val names = group.files.associate { it.first.id to it.first.name }
        return cached.allVersions().all { names[it.driveId] == it.fileName }
    }

    /**
     * Construye la película: 1 búsqueda TMDB + versiones ordenadas.
     * Devuelve la película y si el resultado se puede cachear (un fallo
     * por límite de TMDB no se cachea: se reintenta en la próxima carga).
     */
    private suspend fun buildMovie(group: Group, apiKey: String?): Pair<Movie, Boolean> {
        val sorted = group.files.sortedBy { FilenameParser.qualityRank(it.second) }
        val versions = sorted.map { (file, p) ->
            MovieVersion(file.id, file.name, FilenameParser.versionLabel(p))
        }
        val best = versions.first()
        val dolbyVision = group.files.any { it.second.dolbyVision }
        val resolved = resolveTitle(group.title, group.year, apiKey)
        val movie = when (resolved) {
            is ResolveResult.Found -> Movie(
                driveId = best.driveId,
                fileName = best.fileName,
                title = resolved.value.title,
                year = resolved.value.year,
                overview = resolved.value.overview,
                genres = resolved.value.genres,
                posterUrl = resolved.value.posterUrl,
                backdropUrl = resolved.value.backdropUrl,
                isSeries = resolved.value.isSeries,
                versions = versions,
                dolbyVision = dolbyVision
            )
            else -> Movie(
                driveId = best.driveId,
                fileName = best.fileName,
                title = group.title,
                year = group.year,
                overview = "",
                genres = emptyList(),
                posterUrl = null,
                backdropUrl = null,
                versions = versions,
                dolbyVision = dolbyVision
            )
        }
        return movie to (resolved !is ResolveResult.RateLimited)
    }

    private data class Resolved(
        val title: String,
        val year: Int?,
        val overview: String,
        val genres: List<String>,
        val posterUrl: String?,
        val backdropUrl: String?,
        val isSeries: Boolean
    )

    private sealed interface ResolveResult {
        data class Found(val value: Resolved) : ResolveResult
        /** TMDB no conoce el título: tarjeta de respaldo (se cachea). */
        data object NoMatch : ResolveResult
        /** Límite 429 agotado: NO se cachea, se reintenta luego. */
        data object RateLimited : ResolveResult
    }

    /**
     * Busca en TMDB (película y luego serie).
     * v0.7.2: si TMDB responde 429, espera 10 s y reintenta hasta 5 veces;
     * si se agotan, devuelve RateLimited para no cachear el fallo.
     */
    private suspend fun resolveTitle(
        title: String,
        year: Int?,
        apiKey: String?
    ): ResolveResult {
        if (apiKey.isNullOrBlank()) return ResolveResult.NoMatch
        var attempts429 = 0
        while (true) {
            try {
                // 1) Película.
                tmdb.searchMovie(apiKey, title, year)?.let {
                    val genres = tmdb.genreNames(apiKey)
                    return ResolveResult.Found(
                        Resolved(
                            title = it.title.ifBlank { title },
                            year = it.year ?: year,
                            overview = it.overview,
                            genres = it.genreIds.mapNotNull { genres[it] }.take(3),
                            posterUrl = tmdb.posterUrl(it.posterPath),
                            backdropUrl = tmdb.backdropUrl(it.backdropPath),
                            isSeries = false
                        )
                    )
                }
                // 2) Serie (v0.7.0): muchos archivos sueltos son series.
                tmdb.searchTv(apiKey, title, year)?.let {
                    val genres = tmdb.tvGenreNames(apiKey)
                    return ResolveResult.Found(
                        Resolved(
                            title = it.title.ifBlank { title },
                            year = it.year ?: year,
                            overview = it.overview,
                            genres = it.genreIds.mapNotNull { genres[it] }.take(3),
                            posterUrl = tmdb.posterUrl(it.posterPath),
                            backdropUrl = tmdb.backdropUrl(it.backdropPath),
                            isSeries = true
                        )
                    )
                }
                return ResolveResult.NoMatch
            } catch (e: TmdbException) {
                if (e.code == 429 && attempts429 < MAX_429_RETRIES) {
                    attempts429++
                    delay(10_000)
                } else if (e.code == 429) {
                    return ResolveResult.RateLimited
                } else {
                    // Sin red, key inválida u otro error: tarjeta de respaldo.
                    return ResolveResult.NoMatch
                }
            }
        }
    }

    companion object {
        private const val MAX_429_RETRIES = 5
    }

    // ---------- Caché local (v2: por grupo, con versiones) ----------

    private fun cacheFile(): File = File(context.filesDir, "library_cache_v2.json")

    private fun readCache(): Map<String, Movie> {
        val f = cacheFile()
        if (!f.exists()) return emptyMap()
        return runCatching {
            val json = JSONObject(f.readText())
            buildMap {
                json.keys().forEach { key ->
                    val o = json.getJSONObject(key)
                    val genres = o.optJSONArray("genres")?.let { arr ->
                        buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
                    } ?: emptyList()
                    val versions = o.optJSONArray("versions")?.let { arr ->
                        buildList {
                            for (i in 0 until arr.length()) {
                                val v = arr.getJSONObject(i)
                                add(
                                    MovieVersion(
                                        driveId = v.getString("driveId"),
                                        fileName = v.optString("fileName", ""),
                                        label = v.optString("label", "Archivo")
                                    )
                                )
                            }
                        }
                    } ?: emptyList()
                    val best = versions.firstOrNull()
                    // v0.7.3: el flag puede faltar en cachés viejos; se
                    // deriva de los nombres de archivo como respaldo.
                    val dolbyVision = o.optBoolean("dolbyVision", false) ||
                        versions.any {
                            runCatching { FilenameParser.parse(it.fileName).dolbyVision }
                                .getOrDefault(false)
                        }
                    put(
                        key, Movie(
                            driveId = best?.driveId ?: o.optString("driveId", ""),
                            fileName = best?.fileName ?: o.optString("fileName", ""),
                            title = o.optString("title", ""),
                            year = o.optInt("year", -1).takeIf { it > 0 },
                            overview = o.optString("overview", ""),
                            genres = genres,
                            posterUrl = o.optString("posterUrl", null),
                            backdropUrl = o.optString("backdropUrl", null),
                            isSeries = o.optBoolean("isSeries", false),
                            versions = versions,
                            dolbyVision = dolbyVision
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeCache(cache: Map<String, Movie>) {
        runCatching {
            val json = JSONObject()
            cache.forEach { (key, m) ->
                json.put(key, JSONObject().apply {
                    put("title", m.title)
                    put("year", m.year ?: -1)
                    put("overview", m.overview)
                    put("genres", JSONArray(m.genres))
                    put("posterUrl", m.posterUrl)
                    put("backdropUrl", m.backdropUrl)
                    put("isSeries", m.isSeries)
                    put("dolbyVision", m.dolbyVision)
                    put("versions", JSONArray().apply {
                        m.allVersions().forEach { v ->
                            put(JSONObject().apply {
                                put("driveId", v.driveId)
                                put("fileName", v.fileName)
                                put("label", v.label)
                            })
                        }
                    })
                })
            }
            cacheFile().writeText(json.toString())
        }
    }
}
