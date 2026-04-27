package com.scrudio.tv.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Domain representation of a TMDB movie or TV show.
 *
 * `@Parcelize` — pass via Intent extras (Home → Details, etc.).
 * `@Serializable` — stored as JSON in [FavoritesRepository]'s DataStore.
 */
@Parcelize
@Serializable
data class MediaItem(
    val tmdbId: Long,
    val type: MediaType,
    val title: String,
    val year: String,            // "" if unknown
    val overview: String,
    val posterUrl: String?,      // ready-to-load (https://image.tmdb.org/t/p/w342…)
    val backdropUrl: String?,    // w780
    val voteAverage: Double,
    val imdbId: String? = null   // populated lazily via /external_ids
) : Parcelable {
    /** Stable cache id for ImageCardView and adapters. */
    val cardId: Long get() = tmdbId * 10 + (if (type == MediaType.TV) 1 else 0)
}
