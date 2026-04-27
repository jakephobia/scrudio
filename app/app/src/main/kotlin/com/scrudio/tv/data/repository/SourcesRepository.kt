package com.scrudio.tv.data.repository

import android.content.Context
import android.util.Log
import com.scrudio.tv.data.api.HttpModule
import com.scrudio.tv.data.api.TorrentioApi
import com.scrudio.tv.data.model.MediaType
import com.scrudio.tv.data.model.Quality
import com.scrudio.tv.data.model.StreamSource
import com.scrudio.tv.data.parser.StreamParser
import com.scrudio.tv.data.settings.ScrudioSettings

/**
 * Wraps Torrentio (and any future Stremio-compatible aggregator) into a
 * single coroutine-friendly call.
 *
 * Public API: [getStreams] returns an empty list for any failure mode
 * (no RD key, no IMDB id, network error, empty payload). UI just maps
 * `isEmpty()` → "No playable sources".
 */
class SourcesRepository private constructor(
    private val api: TorrentioApi,
    private val settings: ScrudioSettings,
    private val auth: RealDebridAuthRepository
) {
    /**
     * @param imdbId   tt-prefixed IMDB id (movies and TV)
     * @param type     MOVIE or TV
     * @param season   1-based, ignored for movies
     * @param episode  1-based, ignored for movies
     */
    suspend fun getStreams(
        imdbId: String,
        type: MediaType,
        season: Int = 0,
        episode: Int = 0
    ): List<StreamSource> {
        if (imdbId.isBlank()) return emptyList()
        if (type == MediaType.TV && (season <= 0 || episode <= 0)) return emptyList()

        val sid = if (type == MediaType.MOVIE) imdbId else "$imdbId:$season:$episode"

        // Get all torrents (including RARBG and other providers)
        val allTorrents = try {
            val raw = api.streams(
                config = TorrentioApi.CONFIG_EMPTY,
                type = type.torrentioType,
                id = sid
            )
            StreamParser.parseStreams(
                raw = raw.streams,
                allowedQualities = ALLOWED_QUALITIES,
                dropNoSeeds = settings.hideNoSeeds()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Torrentio (all torrents) failure for $sid", e)
            emptyList()
        }

        // Get RD-cached torrents
        val rdTorrents = try {
            val token = auth.accessToken()
            if (token.isNotEmpty()) {
                val config = "${TorrentioApi.CONFIG_RD_PREFIX}$token"
                val raw = api.streams(
                    config = config,
                    type = type.torrentioType,
                    id = sid
                )
                StreamParser.parseStreams(
                    raw = raw.streams,
                    allowedQualities = ALLOWED_QUALITIES,
                    dropNoSeeds = settings.hideNoSeeds()
                )
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Torrentio (RD) failure for $sid", e)
            emptyList()
        }

        // Merge: keep RD-cached versions, add non-RD torrents
        val rdHashes = rdTorrents.mapNotNull { it.infoHash }.toSet()
        val merged = rdTorrents + allTorrents.filter { it.infoHash !in rdHashes }

        if (merged.isEmpty()) Log.i(TAG, "No playable streams for $sid")
        else Log.i(TAG, "Total: ${merged.size} streams (RD: ${rdTorrents.size}, others: ${allTorrents.size - rdTorrents.size}) for $sid")

        return merged
    }

    companion object {
        private const val TAG = "SourcesRepository"
        private val ALLOWED_QUALITIES = setOf(
            Quality.UHD_4K, Quality.FHD_1080P, Quality.HD_720P, Quality.SD_480P
        )

        @Volatile private var instance: SourcesRepository? = null

        fun get(context: Context): SourcesRepository =
            instance ?: synchronized(this) {
                instance ?: SourcesRepository(
                    api = HttpModule.retrofit(context, TorrentioApi.BASE_URL).create(TorrentioApi::class.java),
                    settings = ScrudioSettings.get(context),
                    auth = RealDebridAuthRepository.get(context)
                ).also { instance = it }
            }
    }
}
