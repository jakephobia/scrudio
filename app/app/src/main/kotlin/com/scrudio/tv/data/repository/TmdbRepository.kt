package com.scrudio.tv.data.repository

import android.content.Context
import com.scrudio.tv.data.api.HttpModule
import com.scrudio.tv.data.api.TmdbApi
import com.scrudio.tv.data.dto.TmdbItemDto
import com.scrudio.tv.data.dto.TmdbPagedResponse
import com.scrudio.tv.data.model.MediaItem
import com.scrudio.tv.data.model.MediaType
import com.scrudio.tv.data.settings.ScrudioSettings

/**
 * Single entry point for catalog data.
 *
 * Hides:
 *  - API-key/language injection (read once per call from settings)
 *  - DTO → domain model mapping
 *  - "force a media type" logic for popular_movies / popular_tv (which don't
 *    return media_type in their payload)
 */
class TmdbRepository private constructor(
    private val api: TmdbApi,
    private val settings: ScrudioSettings
) {
    suspend fun trending(page: Int = 1): List<MediaItem> =
        api.trending(
            apiKey = settings.tmdbKey(),
            language = settings.language(),
            page = page
        ).toItems(forcedType = null)

    suspend fun popularMovies(page: Int = 1): List<MediaItem> =
        api.popularMovies(
            apiKey = settings.tmdbKey(),
            language = settings.language(),
            page = page
        ).toItems(forcedType = MediaType.MOVIE)

    suspend fun popularTv(page: Int = 1): List<MediaItem> =
        api.popularTv(
            apiKey = settings.tmdbKey(),
            language = settings.language(),
            page = page
        ).toItems(forcedType = MediaType.TV)

    suspend fun topRatedMovies(page: Int = 1): List<MediaItem> =
        api.topRatedMovies(
            apiKey = settings.tmdbKey(),
            language = settings.language(),
            page = page
        ).toItems(forcedType = MediaType.MOVIE)

    suspend fun topRatedTv(page: Int = 1): List<MediaItem> =
        api.topRatedTv(
            apiKey = settings.tmdbKey(),
            language = settings.language(),
            page = page
        ).toItems(forcedType = MediaType.TV)

    suspend fun search(query: String, page: Int = 1): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        return api.searchMulti(
            apiKey = settings.tmdbKey(),
            language = settings.language(),
            query = query,
            page = page
        ).toItems(forcedType = null)
    }

    /** Returns the list of seasons for a TV show, excluding "Specials" (number 0). */
    suspend fun seasonsOf(media: MediaItem): List<com.scrudio.tv.data.model.Season> {
        if (media.type != MediaType.TV) return emptyList()
        val detail = api.tvDetails(
            id = media.tmdbId,
            apiKey = settings.tmdbKey(),
            language = settings.language()
        )
        return detail.seasons
            .filter { it.seasonNumber > 0 }
            .map { s ->
                com.scrudio.tv.data.model.Season(
                    number = s.seasonNumber,
                    name = s.name.orEmpty().ifEmpty { "Season ${s.seasonNumber}" },
                    overview = s.overview.orEmpty(),
                    episodeCount = s.episodeCount,
                    posterUrl = s.posterPath?.let { TmdbApi.IMG_POSTER + it } ?: media.posterUrl,
                    airDate = s.airDate
                )
            }
    }

    /** Returns the episodes of a given season, in TMDB order. */
    suspend fun episodesOf(media: MediaItem, season: Int): List<com.scrudio.tv.data.model.Episode> {
        if (media.type != MediaType.TV || season <= 0) return emptyList()
        val detail = api.tvSeason(
            id = media.tmdbId,
            season = season,
            apiKey = settings.tmdbKey(),
            language = settings.language()
        )
        return detail.episodes.map { e ->
            com.scrudio.tv.data.model.Episode(
                number = e.episodeNumber,
                seasonNumber = e.seasonNumber,
                name = e.name.orEmpty().ifEmpty { "Episode ${e.episodeNumber}" },
                overview = e.overview.orEmpty(),
                stillUrl = e.stillPath?.let { TmdbApi.IMG_BACKDROP + it } ?: media.backdropUrl,
                airDate = e.airDate,
                voteAverage = e.voteAverage,
                runtimeMinutes = e.runtime
            )
        }
    }

    /** Lightweight: fetch only the IMDB id (used right before calling Torrentio). */
    suspend fun imdbId(item: MediaItem): String? {
        item.imdbId?.let { return it }
        val key = settings.tmdbKey()
        return when (item.type) {
            MediaType.MOVIE -> api.movieExternalIds(item.tmdbId, key).imdbId
            MediaType.TV -> api.tvExternalIds(item.tmdbId, key).imdbId
        }
    }

    // ── DTO → domain mapping ────────────────────────────────────────────────
    private fun TmdbPagedResponse.toItems(forcedType: MediaType?): List<MediaItem> =
        results.mapNotNull { it.toDomain(forcedType) }

    private fun TmdbItemDto.toDomain(forcedType: MediaType?): MediaItem? {
        val type = forcedType
            ?: MediaType.detect(mediaType, hasFirstAirDate = !firstAirDate.isNullOrEmpty())
            ?: return null    // skip persons

        val displayTitle = when (type) {
            MediaType.TV -> name ?: title.orEmpty()
            MediaType.MOVIE -> title ?: name.orEmpty()
        }

        val date = if (type == MediaType.TV) firstAirDate else releaseDate
        val year = date?.substringBefore('-').orEmpty()

        return MediaItem(
            tmdbId = id,
            type = type,
            title = displayTitle,
            year = year,
            overview = overview.orEmpty(),
            posterUrl = posterPath?.let { TmdbApi.IMG_POSTER + it },
            backdropUrl = backdropPath?.let { TmdbApi.IMG_BACKDROP + it },
            voteAverage = voteAverage
        )
    }

    companion object {
        @Volatile private var instance: TmdbRepository? = null

        fun get(context: Context): TmdbRepository =
            instance ?: synchronized(this) {
                instance ?: TmdbRepository(
                    api = HttpModule.retrofit(context, TmdbApi.BASE_URL).create(TmdbApi::class.java),
                    settings = ScrudioSettings.get(context)
                ).also { instance = it }
            }
    }
}
