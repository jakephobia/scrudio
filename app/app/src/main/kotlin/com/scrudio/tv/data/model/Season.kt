package com.scrudio.tv.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** A season belonging to a TV show. */
@Parcelize
data class Season(
    val number: Int,
    val name: String,
    val overview: String,
    val episodeCount: Int,
    val posterUrl: String?,
    val airDate: String?
) : Parcelable

/** An episode belonging to a season. */
@Parcelize
data class Episode(
    val number: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val stillUrl: String?,        // 16:9 still — TMDB w780 path
    val airDate: String?,
    val voteAverage: Double,
    val runtimeMinutes: Int?
) : Parcelable
