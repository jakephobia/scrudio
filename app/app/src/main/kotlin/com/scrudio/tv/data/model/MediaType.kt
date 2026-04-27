package com.scrudio.tv.data.model

/**
 * Two-state enum used everywhere a TMDB item is referenced.
 *
 * `apiPath` matches TMDB's URL segment ("movie"/"tv") and `torrentioType`
 * matches Torrentio's own path segment ("movie"/"series"). Keeping them on
 * the enum avoids string magic in the repositories.
 */
@kotlinx.serialization.Serializable
enum class MediaType(val apiPath: String, val torrentioType: String) {
    MOVIE(apiPath = "movie", torrentioType = "movie"),
    TV(apiPath = "tv", torrentioType = "series");

    companion object {
        /** Mirror of `tmdb.detect_media_type()` from the Kodi add-on. */
        fun detect(rawMediaType: String?, hasFirstAirDate: Boolean): MediaType? = when (rawMediaType) {
            "movie" -> MOVIE
            "tv" -> TV
            "person" -> null
            else -> if (hasFirstAirDate) TV else MOVIE
        }
    }
}
