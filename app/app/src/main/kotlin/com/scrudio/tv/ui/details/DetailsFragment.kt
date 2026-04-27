package com.scrudio.tv.ui.details

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper
import androidx.leanback.widget.OnActionClickedListener
import coil.imageLoader
import coil.request.ImageRequest
import com.scrudio.tv.R
import androidx.lifecycle.lifecycleScope
import com.scrudio.tv.data.model.MediaItem
import com.scrudio.tv.data.model.MediaType
import com.scrudio.tv.data.repository.FavoritesRepository
import com.scrudio.tv.ui.seasons.SeasonsActivity
import com.scrudio.tv.ui.sources.SourcesActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Leanback details screen.
 *
 * Layout (provided by `FullWidthDetailsOverviewRowPresenter`):
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  poster     title (year)                                 │
 *   │             vote · plot                                  │
 *   │             [Play] [Sources] [Seasons]                   │
 *   └──────────────────────────────────────────────────────────┘
 *
 * Backdrop fanart fills the area behind. Actions are D-pad-focusable.
 */
class DetailsFragment : DetailsSupportFragment() {

    private lateinit var media: MediaItem
    private val descriptionPresenter = MediaDescriptionPresenter()
    private lateinit var actionsAdapter: ArrayObjectAdapter
    private lateinit var favoriteAction: Action
    private var isFavorite: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        media = requireNotNull(arguments?.getParcelable(ARG_MEDIA))
        setupAdapter()
        loadFanart()
    }

    private fun setupAdapter() {
        val rowPresenter = FullWidthDetailsOverviewRowPresenter(descriptionPresenter).apply {
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.sage_browse_background)
            // Dark sage on dark background: keeps default light Leanback action labels readable.
            actionsBackgroundColor =
                ContextCompat.getColor(requireContext(), R.color.sage_browse_actions)
            // Slightly larger logo (poster) area — 240dp tall
            isParticipatingEntranceTransition = false
            FullWidthDetailsOverviewSharedElementHelper().setSharedElementEnterTransition(
                activity, "details_poster"
            )
        }

        rowPresenter.onActionClickedListener = OnActionClickedListener { action ->
            when (action.id) {
                ACTION_PLAY, ACTION_SOURCES -> openSources()
                ACTION_SEASONS -> SeasonsActivity.start(requireContext(), media)
                ACTION_FAVORITE -> toggleFavorite()
            }
        }

        val presenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(DetailsOverviewRow::class.java, rowPresenter)
        }
        val rowsAdapter = ArrayObjectAdapter(presenterSelector)

        val overview = DetailsOverviewRow(media)
        favoriteAction = Action(ACTION_FAVORITE, getString(R.string.action_favorite_add))
        actionsAdapter = ArrayObjectAdapter().apply {
            when (media.type) {
                MediaType.MOVIE -> {
                    add(Action(ACTION_PLAY, getString(R.string.action_play)))
                    add(Action(ACTION_SOURCES, getString(R.string.action_sources)))
                }
                MediaType.TV -> {
                    add(Action(ACTION_SEASONS, getString(R.string.action_seasons)))
                }
            }
            add(favoriteAction)
        }
        overview.actionsAdapter = actionsAdapter
        loadFavoriteState()
        loadPosterInto(overview)
        rowsAdapter.add(overview)
        adapter = rowsAdapter
    }

    private fun loadPosterInto(row: DetailsOverviewRow) {
        val url = media.posterUrl ?: return
        val request = ImageRequest.Builder(requireContext())
            .data(url)
            .target(
                onSuccess = { d: Drawable -> row.imageDrawable = d },
                onError = { /* leave blank */ }
            )
            .build()
        requireContext().imageLoader.enqueue(request)
    }

    private fun loadFanart() {
        val url = media.backdropUrl ?: return
        val helper = androidx.leanback.app.DetailsSupportFragmentBackgroundController(this)
        helper.enableParallax()
        val request = ImageRequest.Builder(requireContext())
            .data(url)
            .target(
                onSuccess = { d -> helper.coverBitmap = (d as? android.graphics.drawable.BitmapDrawable)?.bitmap }
            )
            .build()
        requireContext().imageLoader.enqueue(request)
    }

    private fun openSources() {
        SourcesActivity.start(requireContext(), media)
    }

    private fun loadFavoriteState() {
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) {
                FavoritesRepository.get(requireContext()).isFavorite(media)
            }
            isFavorite = state
            updateFavoriteLabel()
        }
    }

    private fun toggleFavorite() {
        lifecycleScope.launch {
            val newState = withContext(Dispatchers.IO) {
                FavoritesRepository.get(requireContext()).toggle(media)
            }
            isFavorite = newState
            updateFavoriteLabel()
        }
    }

    private fun updateFavoriteLabel() {
        favoriteAction.label1 = getString(
            if (isFavorite) R.string.action_favorite_remove else R.string.action_favorite_add
        )
        // Force the action row to re-render with the new label
        val pos = actionsAdapter.indexOf(favoriteAction)
        if (pos >= 0) actionsAdapter.notifyArrayItemRangeChanged(pos, 1)
    }

    companion object {
        private const val ARG_MEDIA = "media"
        const val ACTION_PLAY = 1L
        const val ACTION_SOURCES = 2L
        const val ACTION_SEASONS = 3L
        const val ACTION_FAVORITE = 4L

        fun newInstance(item: MediaItem) = DetailsFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_MEDIA, item) }
        }
    }
}
