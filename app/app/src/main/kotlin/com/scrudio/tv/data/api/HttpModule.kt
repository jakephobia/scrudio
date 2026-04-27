package com.scrudio.tv.data.api

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.scrudio.tv.BuildConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for HTTP plumbing.
 *
 * - One [OkHttpClient] is reused across all Retrofit instances → connection
 *   pool is shared, RAM stays low (Sage v2 commandment #2).
 * - `ignoreUnknownKeys = true` lets us add fields to TMDB/Torrentio in the
 *   future without breaking older builds in the wild.
 * - 8 MB on-disk HTTP cache: TMDB responses are cacheable for hours, this
 *   spares re-fetches when the user pages back to a screen.
 */
object HttpModule {

    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Volatile private var client: OkHttpClient? = null

    fun okHttp(context: Context): OkHttpClient {
        client?.let { return it }
        synchronized(this) {
            client?.let { return it }

            val cacheDir = File(context.cacheDir, "http")
            val cache = Cache(cacheDir, CACHE_SIZE_BYTES)

            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            }

            val built = OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("X-User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(req)
                }
                .addInterceptor(logging)
                .build()

            client = built
            return built
        }
    }

    fun retrofit(context: Context, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp(context))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    fun openSubtitlesApi(context: Context): OpenSubtitlesApi =
        retrofit(context, BuildConfig.OPENSUBTITLES_BASE_URL)
            .create(OpenSubtitlesApi::class.java)

    private const val CACHE_SIZE_BYTES = 8L * 1024 * 1024
    private const val USER_AGENT = "Scrudio/0.1 (AndroidTV)"
}
