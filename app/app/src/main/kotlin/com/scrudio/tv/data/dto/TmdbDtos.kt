package com.scrudio.tv.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs for TMDB v3.
 *
 * Only fields actually consumed are declared; `Json { ignoreUnknownKeys = true }`
 * is set centrally in HttpModule so the rest is silently dropped.
 */
@Serializable
data class TmdbPagedResponse(
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
    val results: List<TmdbItemDto> = emptyList()
)

@Serializable
data class TmdbItemDto(
    val id: Long,
    @SerialName("media_type") val mediaType: String? = null,    // present on /trending and /search/multi
    val title: String? = null,                                  // movies
    val name: String? = null,                                   // tv shows
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0
)

@Serializable
data class TmdbExternalIdsDto(
    val id: Long? = null,
    @SerialName("imdb_id") val imdbId: String? = null
)

@Serializable
data class TmdbMovieDetailDto(
    val id: Long,
    val title: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("external_ids") val externalIds: TmdbExternalIdsDto? = null
)

@Serializable
data class TmdbTvDetailDto(
    val id: Long,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("external_ids") val externalIds: TmdbExternalIdsDto? = null,
    val seasons: List<TmdbSeasonStubDto> = emptyList()
)

@Serializable
data class TmdbSeasonStubDto(
    val id: Long? = null,
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("air_date") val airDate: String? = null
)

@Serializable
data class TmdbSeasonDetailDto(
    val id: Long? = null,
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    val episodes: List<TmdbEpisodeDto> = emptyList()
)

@Serializable
data class TmdbEpisodeDto(
    val id: Long,
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val runtime: Int? = null
)
