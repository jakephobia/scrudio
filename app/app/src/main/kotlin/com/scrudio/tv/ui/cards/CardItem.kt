package com.scrudio.tv.ui.cards

import com.scrudio.tv.data.model.MediaItem

/**
 * UI projection of a [MediaItem] sized for an `ImageCardView`.
 *
 * `media` is kept around so the click handler can navigate without
 * round-tripping through TMDB again.
 */
data class CardItem(
    val id: Long,
    val title: String,
    val subtitle: String = "",
    val posterUrl: String? = null,
    val media: MediaItem? = null
) {
    companion object {
        fun fromMedia(item: MediaItem): CardItem = CardItem(
            id = item.cardId,
            title = item.title,
            subtitle = listOfNotNull(
                item.year.takeIf { it.isNotEmpty() },
                if (item.voteAverage > 0) "★ %.1f".format(item.voteAverage) else null
            ).joinToString(" · "),
            posterUrl = item.posterUrl,
            media = item
        )
    }
}
