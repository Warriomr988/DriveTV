package com.panda.drivetv.library

/**
 * Limpia nombres de archivo tipo "Dune.Parte.2.(2024).[1080p].BluRay.x264.mkv"
 * → título "Dune Parte 2", año 2024 (v0.6.0 "Biblioteca").
 *
 * v0.7.1: además detecta calidad ("4K", "1080p"…) y fuente ("Remux",
 * "BluRay", "WEB-DL"…) para agrupar versiones de la misma película, y
 * conoce las etiquetas de las colecciones con muchas calidades
 * ("Blu ray", "AVC", "MA 5 1", "HDH", "WEB DL", "BR rip", "BD remux"…).
 *
 * Heurística:
 * - El año es el último token de 4 dígitos (19xx/20xx); "(2024)" vale igual.
 * - Se quitan etiquetas de calidad/códec/idioma/fuente.
 * - Si no queda título, se usa el nombre base como respaldo.
 */
object FilenameParser {

    data class Parsed(
        val title: String,
        val year: Int?,
        /** "4K", "1080p", "720p", "480p" o null. */
        val quality: String? = null,
        /** "Remux", "BluRay", "WEB-DL", "HDTV", "DVD", "CAM" o null. */
        val source: String? = null,
        /** Episodio normalizado ("S01E02", "1X02") o null si no es serie. */
        val episode: String? = null,
        /** true si el nombre indica Dolby Vision (v0.7.3). */
        val dolbyVision: Boolean = false
    )

    private val YEAR = Regex("""\b(19\d{2}|20\d{2})\b""")
    /** Marcadores de episodio/temporada: S01E02, s1e2, 1x02, S01 suelto. */
    private val EPISODE = Regex("""(?i)\b(?:s\d{1,2}e\d{1,2}|\d{1,2}x\d{1,2}|s\d{1,2})\b""")

    /** Etiquetas compuestas (con espacio): se borran antes de tokenizar. */
    private val PHRASE_TAGS = listOf(
        "blu ray", "web dl", "br rip", "bd rip", "bd remux",
        "hd ts", "hd cam", "ma 5 1", "ma 51", "ma 7 1", "dts ma"
    )

    private val TAGS = setOf(
        // calidad / resolución
        "2160p", "1080p", "720p", "480p", "2160", "1080", "720",
        "4k", "uhd", "fhd", "fullhd", "hd",
        // fuente
        "blueray", "bluray", "brrip", "bdrip", "web-dl", "webdl", "webrip",
        "web", "hdrip", "hdtv", "dvdrip", "dvd", "dvdscr", "screener",
        "remux", "cam", "telesync", "ts", "tc", "r5", "hdts", "hdcam",
        "br", "bd", "rip", "full", "bdremux",
        // plataformas de streaming (tags de release)
        "amzn", "amnz", "amazon", "nf", "netflix", "dsnp", "disney",
        "hmax", "atvp", "apple",
        // grupos de release comunes
        "yts", "yify", "rarbg", "etrg", "psa", "dl", "vip", "1link",
        // códec / audio / hdr
        "x264", "x265", "h264", "h265", "hevc", "xvid", "avc",
        "aac", "ac3", "dts", "ddp", "dd", "atmos", "truehd", "flac", "mp3",
        "ma", "hdh",
        "10bit", "8bit", "hdr", "hdr10", "dovi", "dv", "imax",
        // idioma / región
        "dual", "latino", "hdlatino", "castellano", "espanol", "español",
        "portugues", "portugués", "dublado", "legendado",
        "subtitulada", "subs", "sub", "subt",
        "esp", "eng", "spa", "lat", "us", "mx",
        "ingles", "inglés", "english", "spanish", "multi",
        // ediciones / releases
        "extended", "unrated", "directors", "theatrical", "repack",
        "proper", "limited", "internal"
    )

    fun parse(fileName: String): Parsed {
        val base = fileName.substringBeforeLast('.', fileName)
        var work = base

        // Año: el último token de 4 dígitos (los "(2024)" ya son token).
        val years = YEAR.findAll(work).toList()
        val year = years.lastOrNull()?.value?.toIntOrNull()
        if (year != null) {
            val m = years.last()
            work = work.removeRange(m.range.first, m.range.last + 1)
        }
        // Episodio (S01E02): se guarda para no mezclar episodios y se quita
        // del título para buscar la serie en TMDB.
        val episode = EPISODE.find(work)?.value?.uppercase()
        work = EPISODE.replace(work, " ")

        // Separadores → espacios.
        work = work.replace(Regex("""[._\-+]+"""), " ")
        val lowered = work.lowercase()

        // Calidad y fuente: se detectan antes de limpiar.
        val quality = detectQuality(lowered)
        val source = detectSource(lowered)
        // Dolby Vision (v0.7.3): se detecta antes de limpiar porque "dv"/
        // "dovi" son tags que se eliminan del título.
        val dolbyVision = detectDolbyVision(lowered)

        // Etiquetas compuestas y tokens sueltos.
        var cleaned = work
        for (phrase in PHRASE_TAGS) {
            cleaned = cleaned.replace(phrase, " ", ignoreCase = true)
        }
        val words = cleaned.split(Regex("""\s+"""))
            .map { it.trim('[', ']', '(', ')', '{', '}').trimStart('#') }
            .filter { it.isNotBlank() }
        // Nunca se deja el título vacío: si todo eran tags (p. ej. la
        // película "Us"), se conserva al menos la primera palabra.
        val kept = words.filter { it.lowercase() !in TAGS }.ifEmpty { words.take(1) }

        val title = kept.joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { base.trim() }

        return Parsed(
            title = title,
            year = year,
            quality = quality,
            source = source,
            episode = episode,
            dolbyVision = dolbyVision
        )
    }

    private fun detectQuality(lowered: String): String? {
        fun has(vararg alts: String) = alts.any { Regex("""\b$it\b""").containsMatchIn(lowered) }
        return when {
            has("2160p", "4k", "uhd") -> "4K"
            has("1080p?", "fhd", "fullhd") -> "1080p"
            has("720p?") -> "720p"
            has("480p?") -> "480p"
            else -> null
        }
    }

    private fun detectSource(lowered: String): String? {
        fun has(vararg alts: String) = alts.any { Regex("""\b$it\b""").containsMatchIn(lowered) }
        return when {
            has("remux", "bdremux") -> "Remux"
            has("blu ray", "bluray", "brrip", "bdrip", "bd") -> "BluRay"
            has("web dl", "webdl", "webrip", "web-dl") -> "WEB-DL"
            has("hdtv") -> "HDTV"
            has("dvdrip", "dvd") -> "DVD"
            has("hd ts", "hdts", "hdcam", "cam", "telesync", """\bts\b""", """\btc\b""") -> "CAM"
            else -> null
        }
    }

    /**
     * Detecta Dolby Vision por etiquetas del release: "DV", "DoVi",
     * "DolbyVision", "Dolby Vision".
     */
    private fun detectDolbyVision(lowered: String): Boolean {
        return Regex("""\bdv\b""").containsMatchIn(lowered) ||
            lowered.contains("dovi") ||
            lowered.contains("dolbyvision") ||
            lowered.contains("dolby vision")
    }

    /** Orden de calidad para elegir la mejor versión (menor = mejor). */
    fun qualityRank(parsed: Parsed): Int {
        val res = when (parsed.quality) {
            "4K" -> 0
            "1080p" -> 1
            "720p" -> 2
            "480p" -> 3
            else -> 4
        }
        val src = when (parsed.source) {
            "Remux" -> 0
            "BluRay" -> 1
            "WEB-DL" -> 2
            "HDTV" -> 3
            "DVD" -> 4
            "CAM" -> 5
            else -> 6
        }
        return src * 10 + res
    }

    /**
     * Etiqueta visible de la versión: "4K Remux", "1080p BluRay", "BluRay",
     * "S01E02 · 1080p" para episodios…; con "DV" si es Dolby Vision.
     */
    fun versionLabel(parsed: Parsed): String {
        val parts = listOfNotNull(parsed.episode, parsed.quality, parsed.source).toMutableList()
        if (parsed.dolbyVision) parts.add("DV")
        return parts.joinToString(" ").ifBlank { "Archivo" }
    }
}
