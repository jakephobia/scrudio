package com.scrudio.tv.ui.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.scrudio.tv.R
import com.scrudio.tv.data.api.HttpModule
import com.scrudio.tv.data.api.OpenSubtitlesApi
import com.scrudio.tv.data.dto.DownloadLinkRequest
import com.scrudio.tv.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Fullscreen playback host.
 *
 * - Single Activity, no Fragment (saves a layout pass on low-end TVs).
 * - PlayerView's built-in controller handles play/pause/seek/scrubbing
 *   with the D-pad out of the box.
 * - The 302 redirect from Real-Debrid's URL is followed automatically by
 *   ExoPlayer's default HTTP data source.
 */
class PlaybackActivity : FragmentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var statusView: TextView
    private lateinit var openSubtitlesApi: OpenSubtitlesApi
    private var subtitleJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the screen on while playing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_playback)

        playerView = findViewById(R.id.player_view)
        statusView = findViewById(R.id.player_status)
        openSubtitlesApi = HttpModule.openSubtitlesApi(this)

        val source: StreamSource? = intent?.getParcelableExtra(EXTRA_SOURCE)
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val imdbId = intent?.getStringExtra(EXTRA_IMDB_ID)
        val tmdbId = intent?.getStringExtra(EXTRA_TMDB_ID)?.toIntOrNull()

        if (source == null) { finish(); return }

        statusView.text = getString(R.string.playback_buffering)
        startPlayback(source, title, imdbId, tmdbId)
    }

    private fun startPlayback(source: StreamSource, title: String, imdbId: String?, tmdbId: Int?) {
        val exo = ExoPlayer.Builder(this).build().also { player = it }
        playerView.player = exo

        val mediaItem = Media3Item.Builder()
            .setUri(source.url)
            .setMediaId(source.infoHash ?: source.url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(source.displayLabel())
                    .build()
            )
            .build()

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> statusView.text = getString(R.string.playback_buffering)
                    Player.STATE_READY -> {
                        statusView.text = ""
                        // Search for subtitles when player is ready
                        searchAndLoadSubtitles(imdbId, tmdbId)
                    }
                    Player.STATE_ENDED -> finish()
                    Player.STATE_IDLE -> { /* no-op */ }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
                statusView.text = "Error: ${error.errorCodeName}"
            }
        })

        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun searchAndLoadSubtitles(imdbId: String?, tmdbId: Int?) {
        subtitleJob = lifecycleScope.launch {
            try {
                // Try languages in order: English, Italian, then others
                val languagePriority = listOf("en", "it", "es", "fr", "de")
                for (lang in languagePriority) {
                    val response = openSubtitlesApi.searchSubtitles(
                        imdbId = imdbId,
                        tmdbId = tmdbId,
                        languages = lang
                    )
                    if (response.data.isNotEmpty()) {
                        val firstSubtitle = response.data.first()
                        val fileId = firstSubtitle.attributes.files.firstOrNull()?.file_id ?: continue
                        downloadSubtitle(fileId, lang)
                        return@launch
                    }
                }
                Log.d(TAG, "No subtitles found in any language")
            } catch (e: Exception) {
                Log.e(TAG, "Error searching subtitles", e)
            }
        }
    }

    private fun downloadSubtitle(fileId: Int, language: String) {
        lifecycleScope.launch {
            try {
                val response = openSubtitlesApi.getDownloadLink(DownloadLinkRequest(fileId))
                Log.d(TAG, "Subtitle download link: ${response.link}")

                // Download subtitle file
                val subtitleFile = downloadSubtitleFile(response.link, language)
                if (subtitleFile != null) {
                    addSubtitleToPlayer(subtitleFile, language)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading subtitle", e)
            }
        }
    }

    private val subtitleDir by lazy { File(cacheDir, "subtitles").also { if (!it.exists()) it.mkdirs() } }

    private suspend fun downloadSubtitleFile(url: String, language: String): File? = withContext(Dispatchers.IO) {
        try {
            val client = HttpModule.okHttp(this@PlaybackActivity)
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download subtitle: ${response.code}")
                return@withContext null
            }

            val subtitleFile = File(subtitleDir, "subtitle_${language}_${System.currentTimeMillis()}.srt")
            subtitleFile.writeBytes(response.body?.bytes() ?: return@withContext null)

            Log.d(TAG, "Subtitle saved to: ${subtitleFile.absolutePath}")
            subtitleFile
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading subtitle file", e)
            null
        }
    }

    private fun addSubtitleToPlayer(subtitleFile: File, language: String) {
        val exo = player ?: return

        val languageLabel = when (language) {
            "en" -> "English"
            "it" -> "Italiano"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            else -> language.uppercase()
        }

        val subtitleConfig = SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage(language)
            .setLabel(languageLabel)
            .build()

        val currentMediaItem = exo.currentMediaItem ?: return
        val newMediaItem = currentMediaItem.buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        exo.setMediaItem(newMediaItem)
        exo.prepare()

        Log.d(TAG, "Subtitle added to player: $languageLabel")
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        subtitleJob?.cancel()
        subtitleJob = null
        player?.release()
        player = null
    }

    companion object {
        private const val TAG = "PlaybackActivity"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_TITLE = "title"
        const val EXTRA_IMDB_ID = "imdb_id"
        const val EXTRA_TMDB_ID = "tmdb_id"

        fun start(ctx: Context, source: StreamSource, title: String, imdbId: String? = null, tmdbId: Int? = null) {
            ctx.startActivity(
                Intent(ctx, PlaybackActivity::class.java)
                    .putExtra(EXTRA_SOURCE, source)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_IMDB_ID, imdbId)
                    .putExtra(EXTRA_TMDB_ID, tmdbId?.toString())
            )
        }
    }
}
