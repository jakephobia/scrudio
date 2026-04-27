package com.scrudio.tv.ui.sources

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.scrudio.tv.R
import com.scrudio.tv.data.model.MediaItem

class SourcesActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)

        if (savedInstanceState == null) {
            val media: MediaItem? = intent?.getParcelableExtra(EXTRA_MEDIA)
            val season = intent?.getIntExtra(EXTRA_SEASON, 0) ?: 0
            val episode = intent?.getIntExtra(EXTRA_EPISODE, 0) ?: 0
            if (media == null) { finish(); return }
            supportFragmentManager.beginTransaction()
                .replace(R.id.sources_container, SourcesFragment.newInstance(media, season, episode))
                .commit()
        }
    }

    companion object {
        const val EXTRA_MEDIA = "media"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"

        fun start(ctx: Context, media: MediaItem, season: Int = 0, episode: Int = 0) {
            ctx.startActivity(
                Intent(ctx, SourcesActivity::class.java)
                    .putExtra(EXTRA_MEDIA, media)
                    .putExtra(EXTRA_SEASON, season)
                    .putExtra(EXTRA_EPISODE, episode)
            )
        }
    }
}
