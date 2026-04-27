package com.scrudio.tv.data.api

import com.scrudio.tv.BuildConfig
import com.scrudio.tv.data.dto.*
import retrofit2.http.*

/**
 * Retrofit API interface for OpenSubtitles REST API v1.
 * Documentation: https://opensubtitles.stoplight.io/docs/opensubtitles-api/
 */
interface OpenSubtitlesApi {

    @POST("login")
    @Headers("Api-Key: ${BuildConfig.OPENSUBTITLES_API_KEY}")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @GET("subtitles")
    @Headers("Api-Key: ${BuildConfig.OPENSUBTITLES_API_KEY}")
    suspend fun searchSubtitles(
        @Query("imdb_id") imdbId: String? = null,
        @Query("tmdb_id") tmdbId: Int? = null,
        @Query("languages") languages: String = "it",
        @Query("moviehash") movieHash: String? = null,
        @Query("query") query: String? = null
    ): SubtitlesResponse

    @POST("download")
    @Headers("Api-Key: ${BuildConfig.OPENSUBTITLES_API_KEY}")
    suspend fun getDownloadLink(
        @Body request: DownloadLinkRequest
    ): DownloadLinkResponse
}
