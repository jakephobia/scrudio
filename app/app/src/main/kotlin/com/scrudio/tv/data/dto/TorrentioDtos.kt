package com.scrudio.tv.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stremio-compatible streams payload.
 *
 * Both Torrentio and KnightCrawler share this exact shape, which is why the
 * Python addon's `parse_streams()` is provider-agnostic.
 */
@Serializable
data class StremioStreamsResponse(
    val streams: List<StremioStreamDto> = emptyList()
)

@Serializable
data class StremioStreamDto(
    val name: String? = null,        // e.g. "Torrentio\nRD+ 4k\n⚙️ YTS.MX"
    val title: String? = null,       // e.g. "Movie.2024.2160p.HDR…\n👤 42 💾 12.3 GB"
    val url: String? = null,
    @SerialName("infoHash") val infoHash: String? = null,
    @SerialName("fileIdx") val fileIdx: Int? = null,
    val behaviorHints: BehaviorHintsDto? = null
)

@Serializable
data class BehaviorHintsDto(
    val bingeGroup: String? = null,
    val notWebReady: Boolean? = null
)
