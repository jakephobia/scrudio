package com.scrudio.tv.ui.sources

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.scrudio.tv.R
import com.scrudio.tv.data.model.MediaItem
import com.scrudio.tv.data.model.StreamSource
import com.scrudio.tv.ui.playback.PlaybackActivity
import kotlinx.coroutines.launch

/**
 * One-column vertical grid of stream sources, focusable with up/down D-pad.
 *
 * VerticalGridSupportFragment is the canonical Leanback list-of-strings UI
 * (think Netflix's "Episodes" panel) — each row is full-width, navigation
 * is up/down only, with a search/title bar at the top.
 */
class SourcesFragment : VerticalGridSupportFragment() {

    private val viewModel: SourcesViewModel by viewModels()
    private lateinit var media: MediaItem
    private var season = 0
    private var episode = 0

    private val rowsAdapter = ArrayObjectAdapter(SourceRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        media = requireNotNull(arguments?.getParcelable(ARG_MEDIA))
        season = arguments?.getInt(ARG_SEASON, 0) ?: 0
        episode = arguments?.getInt(ARG_EPISODE, 0) ?: 0

        title = if (season > 0 && episode > 0)
            "${media.title} · S$season E$episode"
        else
            media.title

        searchAffordanceColor =
            ContextCompat.getColor(requireContext(), R.color.sage_primary_muted)

        setGridPresenter(
            VerticalGridPresenter(androidx.leanback.widget.FocusHighlight.ZOOM_FACTOR_SMALL).apply {
                numberOfColumns = 1
            }
        )
        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val src = item as? StreamSource ?: return@OnItemViewClickedListener

            if (src.rdCached) {
                // RD cached - use PlaybackActivity
                PlaybackActivity.start(
                    requireContext(),
                    src,
                    media.title,
                    imdbId = media.imdbId,
                    tmdbId = media.tmdbId.toInt()
                )
            } else {
                // Direct torrent - open with external app or show toast
                handleDirectTorrent(src)
            }
        }

        if (savedInstanceState == null) viewModel.load(media, season, episode)
        observeState()
    }

    private fun observeState() {
        viewLifecycleOwnerLiveData.observe(this) { owner ->
            owner ?: return@observe
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.state.collect { state -> render(state) }
                }
            }
        }
    }

    private fun handleDirectTorrent(src: StreamSource) {
        val magnetUri = src.url
        if (magnetUri.startsWith("magnet:")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetUri))
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    "Nessuna app trovata per aprire magnet link",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(
                requireContext(),
                "Formato non supportato per streaming diretto",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun render(state: SourcesViewModel.UiState) {
        when (state) {
            SourcesViewModel.UiState.Loading -> {
                title = getString(R.string.sources_loading)
            }
            SourcesViewModel.UiState.MissingRdKey -> {
                rowsAdapter.clear()
                title = getString(R.string.error_rd_key_missing)
            }
            SourcesViewModel.UiState.MissingImdbId -> {
                rowsAdapter.clear()
                title = getString(R.string.error_no_sources)
            }
            is SourcesViewModel.UiState.Empty -> {
                rowsAdapter.clear()
                title = getString(R.string.error_no_sources)
            }
            is SourcesViewModel.UiState.Ready -> {
                title = "${media.title} · ${state.sources.size}"
                rowsAdapter.setItems(state.sources, null)
            }
        }
    }

    companion object {
        private const val ARG_MEDIA = "media"
        private const val ARG_SEASON = "season"
        private const val ARG_EPISODE = "episode"

        fun newInstance(media: MediaItem, season: Int, episode: Int): SourcesFragment =
            SourcesFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MEDIA, media)
                    putInt(ARG_SEASON, season)
                    putInt(ARG_EPISODE, episode)
                }
            }
    }
}
