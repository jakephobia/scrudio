package com.scrudio.tv.data.api

import com.scrudio.tv.data.dto.TmdbExternalIdsDto
import com.scrudio.tv.data.dto.TmdbMovieDetailDto
import com.scrudio.tv.data.dto.TmdbPagedResponse
import com.scrudio.tv.data.dto.TmdbSeasonDetailDto
import com.scrudio.tv.data.dto.TmdbTvDetailDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB v3 endpoints used by Scrudio.
 *
 * All paths and query semantics are 1:1 with the Python addon's `tmdb.py`,
 * so the catalog rows match exactly.
 */
interface TmdbApi {

    @GET("trending/{mediaType}/{window}")
    suspend fun trending(
        @Path("mediaType") mediaType: String = "all",   // "all" | "movie" | "tv"
        @Path("window") window: String = "week",        // "day" | "week"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("movie/popular")
    suspend fun popularMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("tv/popular")
    suspend fun popularTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("movie/top_rated")
    suspend fun topRatedMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("tv/top_rated")
    suspend fun topRatedTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbPagedResponse

    @GET("movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Long,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("append_to_response") appendToResponse: String = "external_ids"
    ): TmdbMovieDetailDto

    @GET("tv/{id}")
    suspend fun tvDetails(
        @Path("id") id: Long,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("append_to_response") appendToResponse: String = "external_ids"
    ): TmdbTvDetailDto

    @GET("tv/{id}/season/{season}")
    suspend fun tvSeason(
        @Path("id") id: Long,
        @Path("season") season: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String
    ): TmdbSeasonDetailDto

    @GET("movie/{id}/external_ids")
    suspend fun movieExternalIds(
        @Path("id") id: Long,
        @Query("api_key") apiKey: String
    ): TmdbExternalIdsDto

    @GET("tv/{id}/external_ids")
    suspend fun tvExternalIds(
        @Path("id") id: Long,
        @Query("api_key") apiKey: String
    ): TmdbExternalIdsDto

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMG_POSTER = "https://image.tmdb.org/t/p/w342"
        const val IMG_BACKDROP = "https://image.tmdb.org/t/p/w780"
    }
}
