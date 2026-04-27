package com.scrudio.tv.ui.cards

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil.load
import com.scrudio.tv.R

/**
 * Renders a [CardItem] as a Leanback [ImageCardView].
 *
 * Width/height are sized for a TV poster (~270 dp wide). The focus animation
 * (scale + sage glow) comes from `CardFocusHighlight` style applied via theme.
 */
class CardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context

        val card = ImageCardView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH_DP.dp(context), CARD_HEIGHT_DP.dp(context))
            setMainImageScaleType(android.widget.ImageView.ScaleType.CENTER_CROP)
            setInfoAreaBackgroundColor(
                ContextCompat.getColor(context, R.color.sage_surface_variant)
            )
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val card = viewHolder.view as ImageCardView
        val data = item as? CardItem ?: return

        card.titleText = data.title
        card.contentText = data.subtitle

        if (!data.posterUrl.isNullOrEmpty()) {
            card.mainImageView?.load(data.posterUrl) {
                placeholder(R.drawable.placeholder_poster)
                error(R.drawable.placeholder_poster)
                crossfade(false) // no animations on low-end TV
            }
        } else {
            card.mainImageView?.setImageResource(R.drawable.placeholder_poster)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.badgeImage = null
        card.mainImage = null
    }

    private fun Int.dp(ctx: android.content.Context): Int =
        (this * ctx.resources.displayMetrics.density).toInt()

    companion object {
        private const val CARD_WIDTH_DP = 180
        private const val CARD_HEIGHT_DP = 270
    }
}
