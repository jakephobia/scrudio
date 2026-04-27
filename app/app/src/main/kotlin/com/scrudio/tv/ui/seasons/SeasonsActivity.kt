package com.scrudio.tv.ui.seasons

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.scrudio.tv.R
import com.scrudio.tv.data.model.MediaItem

class SeasonsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)   // reuse same FrameLayout host

        if (savedInstanceState == null) {
            val media: MediaItem? = intent?.getParcelableExtra(EXTRA_MEDIA)
            if (media == null) { finish(); return }
            supportFragmentManager.beginTransaction()
                .replace(R.id.sources_container, SeasonsFragment.newInstance(media))
                .commit()
        }
    }

    companion object {
        const val EXTRA_MEDIA = "media"
        fun start(ctx: Context, media: MediaItem) {
            ctx.startActivity(Intent(ctx, SeasonsActivity::class.java).putExtra(EXTRA_MEDIA, media))
        }
    }
}
