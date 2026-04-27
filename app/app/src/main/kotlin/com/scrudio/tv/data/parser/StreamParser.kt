package com.scrudio.tv.data.parser

import com.scrudio.tv.data.dto.StremioStreamDto
import com.scrudio.tv.data.model.Quality
import com.scrudio.tv.data.model.StreamSource

/**
 * Pure transformations over Stremio-style stream payloads.
 *
 * 1:1 port of `torrentio.py` (regexes, ranking, RD detection). Kept stateless
 * and side-effect free so it's trivial to unit-test off-device.
 */
object StreamParser {

    private val SEEDS = Regex("""👤\s*(\d+)""")
    private val SIZE = Regex("""💾\s*([\d.,]+\s*[GMKT]?B)""", RegexOption.IGNORE_CASE)
    private val PROVIDER = Regex("""⚙️\s*([\w.\-]+)""")

    private val SORT_COMPARATOR = compareByDescending<StreamSource> { it.quality.rank }
        .thenByDescending { it.rdCached }
        .thenByDescending { it.seeds }

    fun parseSeeds(title: String?): Int =
        title?.let { SEEDS.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() } ?: 0

    fun parseSize(title: String?): String =
        title?.let { SIZE.find(it)?.groupValues?.getOrNull(1)?.trim() } ?: ""

    fun parseProvider(name: String?): String {
        if (name.isNullOrEmpty()) return "Unknown"
        PROVIDER.find(name)?.groupValues?.getOrNull(1)?.let { return it }
        // Fallback: second line of the multiline name (avoid split array alloc)
        val idx = name.indexOf('\n')
        return if (idx >= 0) name.substring(idx + 1).trim() else "Unknown"
    }

    fun parseCodec(name: String?): String {
        val u = (name ?: "").uppercase()
        return when {
            "HEVC" in u || "H265" in u || "X265" in u -> "HEVC"
            "H264" in u || "X264" in u -> "H.264"
            "AV1" in u -> "AV1"
            else -> ""
        }
    }

    private fun firstLine(s: String?): String =
        (s ?: "").substringBefore('\n').trim()

    /**
     * Filter + normalise a Stremio `streams` array.
     *
     * @param raw            raw DTOs from Torrentio/KnightCrawler/…
     * @param allowedQualities qualities to keep; `HD_OTHER` always passes
     *                       (matches the Kodi addon's "and quality != 'HD'" check)
     * @param dropNoSeeds    when true, sources with 0 seeds are dropped UNLESS
     *                       they carry the RD+ cache tag in their name
     */
    fun parseStreams(
        raw: List<StremioStreamDto>,
        allowedQualities: Set<Quality>,
        dropNoSeeds: Boolean
    ): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        for (s in raw) {
            val url = s.url ?: continue          // No URL = unplayable without a torrent client
            val name = s.name.orEmpty()
            val title = s.title.orEmpty()

            val q = Quality.fromText("$name $title")
            if (q !in allowedQualities && q != Quality.HD_OTHER) continue

            val seeds = parseSeeds(title)
            val rdCached = "RD+" in name
            if (dropNoSeeds && seeds == 0 && !rdCached) continue

            out += StreamSource(
                release = firstLine(title),
                quality = q,
                seeds = seeds,
                fileSize = parseSize(title),
                provider = parseProvider(name),
                codec = parseCodec(name),
                url = url,
                infoHash = s.infoHash,
                rdCached = rdCached
            )
        }
        // Sort: quality DESC, RD-cached first, then seeds DESC
        return out.sortedWith(SORT_COMPARATOR)
    }
}
