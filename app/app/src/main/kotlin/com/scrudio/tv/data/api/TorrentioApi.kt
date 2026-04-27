package com.scrudio.tv.data.api

import com.scrudio.tv.data.dto.StremioStreamsResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Stremio-style stream aggregator endpoint.
 *
 * URL patterns:
 * - <BASE>/realdebrid=<KEY>/stream/<type>/<id>.json (RD cached only)
 * - <BASE>/stream/<type>/<id>.json (all torrents, no RD)
 * - <BASE>/sort=seeders/stream/<type>/<id>.json (sorted by seeders)
 *
 * The "config" segment can be empty for all torrents, or "realdebrid=KEY" for RD only.
 */
interface TorrentioApi {

    @GET("{config}/stream/{type}/{id}.json")
    suspend fun streams(
        @Path("config", encoded = false) config: String,    // "" or "realdebrid=ABCDEF"
        @Path("type") type: String,                         // "movie" | "series"
        @Path("id") id: String                              // imdbId or imdbId:S:E
    ): StremioStreamsResponse

    companion object {
        const val BASE_URL = "https://torrentio.strem.fun/"
        const val CONFIG_RD_PREFIX = "realdebrid="
        const val CONFIG_EMPTY = ""
    }
}
