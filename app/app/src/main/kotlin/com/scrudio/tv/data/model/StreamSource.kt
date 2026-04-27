package com.scrudio.tv.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * One playable source returned by Torrentio (already RD-resolved at the
 * `url` field — Real-Debrid does the 302 redirect when ExoPlayer requests it).
 */
@Parcelize
data class StreamSource(
    val release: String,
    val quality: Quality,
    val seeds: Int,
    val fileSize: String,        // "2.34 GB", "" if unknown
    val provider: String,        // "YTS.MX", "1337x", …
    val codec: String,           // "HEVC" / "H.264" / "AV1" / ""
    val url: String,
    val infoHash: String?,
    val rdCached: Boolean        // "RD+" badge in the original Stremio name
) : Parcelable {
    fun displayLabel(): String {
        val parts = buildList {
            if (rdCached) add("RD+")
            add("[${quality.label}]")
            if (codec.isNotEmpty()) add(codec)
            if (fileSize.isNotEmpty()) add(fileSize)
            if (seeds > 0) add("\uD83D\uDC64$seeds")
        }
        val head = parts.joinToString(" · ")
        val tail = "$provider — $release"
        return "$head  $tail".trim()
    }
}

enum class Quality(val label: String, val rank: Int) {
    UHD_4K("4K", 4),
    FHD_1080P("1080p", 3),
    HD_720P("720p", 2),
    SD_480P("480p", 1),
    HD_OTHER("HD", 0);

    companion object {
        fun fromText(text: String): Quality {
            val u = text.uppercase()
            return when {
                "2160" in u || "4K" in u -> UHD_4K
                "1080" in u -> FHD_1080P
                "720" in u -> HD_720P
                "480" in u -> SD_480P
                else -> HD_OTHER
            }
        }
    }
}
