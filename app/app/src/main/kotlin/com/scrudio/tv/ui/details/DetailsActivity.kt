package com.scrudio.tv.ui.details

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.scrudio.tv.R
import com.scrudio.tv.data.model.MediaItem

/**
 * Hosts [DetailsFragment]. Single Activity per screen keeps Leanback
 * transitions smooth on low-end TVs (no NavController overhead).
 */
class DetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        if (savedInstanceState == null) {
            val media: MediaItem? = intent?.getParcelableExtra(EXTRA_MEDIA)
            if (media == null) { finish(); return }
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_container, DetailsFragment.newInstance(media))
                .commit()
        }
    }

    companion object {
        const val EXTRA_MEDIA = "media"

        fun start(ctx: Context, item: MediaItem) {
            ctx.startActivity(
                Intent(ctx, DetailsActivity::class.java).putExtra(EXTRA_MEDIA, item)
            )
        }
    }
}
