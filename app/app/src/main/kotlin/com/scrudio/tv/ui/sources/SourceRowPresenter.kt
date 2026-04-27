package com.scrudio.tv.ui.sources

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.scrudio.tv.R
import com.scrudio.tv.data.model.StreamSource

/**
 * Renders a single [StreamSource] as a wide focusable row.
 *
 * Custom layout instead of ImageCardView because we don't have an image,
 * we just want big readable text suitable for 3 m viewing distance.
 */
class SourceRowPresenter : Presenter() {

    private class RowViewHolder(view: android.view.View) : ViewHolder(view) {
        val quality: android.widget.TextView = view.findViewById(R.id.row_quality)
        val title: android.widget.TextView = view.findViewById(R.id.row_title)
        val meta: android.widget.TextView = view.findViewById(R.id.row_meta)
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.source_row, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val src = item as? StreamSource ?: return
        val vh = viewHolder as RowViewHolder

        vh.quality.text = src.quality.label
        vh.title.text = src.release

        vh.meta.text = buildString(64) {
            if (src.rdCached) append("RD+") else append("📥")
            append(" · ")
            if (src.codec.isNotEmpty()) { append(src.codec); append(" · ") }
            if (src.fileSize.isNotEmpty()) { append(src.fileSize); append(" · ") }
            if (src.seeds > 0) { append("👤 "); append(src.seeds); append(" · ") }
            append(src.provider)
        }

        val colorRes = if (src.rdCached) R.color.sage else R.color.secondary
        vh.meta.setTextColor(ContextCompat.getColor(vh.view.context, colorRes))
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) { /* no-op */ }
}
