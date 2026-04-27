package com.scrudio.tv.ui.details

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.scrudio.tv.data.model.MediaItem

/**
 * Renders the textual block (title / year+vote / plot) inside the details
 * overview row. The presenter superclass handles all the leanback-specific
 * focus and layout plumbing.
 */
class MediaDescriptionPresenter : AbstractDetailsDescriptionPresenter() {
    override fun onBindDescription(vh: ViewHolder, item: Any) {
        val media = item as MediaItem
        vh.title.text = media.title
        val sub = buildList {
            if (media.year.isNotEmpty()) add(media.year)
            if (media.voteAverage > 0) add("★ %.1f".format(media.voteAverage))
        }.joinToString("  ·  ")
        vh.subtitle.text = sub
        vh.body.text = media.overview
    }
}
