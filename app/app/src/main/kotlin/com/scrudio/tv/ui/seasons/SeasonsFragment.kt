package com.scrudio.tv.ui.seasons

import android.app.Application
import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import coil.load
import com.scrudio.tv.R
import com.scrudio.tv.data.model.MediaItem
import com.scrudio.tv.data.model.Season
import com.scrudio.tv.data.repository.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SeasonsFragment : VerticalGridSupportFragment() {

    private lateinit var media: MediaItem
    private val viewModel: SeasonsViewModel by viewModels()
    private val rowsAdapter = ArrayObjectAdapter(SeasonPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        media = requireNotNull(arguments?.getParcelable(ARG_MEDIA))
        title = media.title

        setGridPresenter(
            VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL).apply { numberOfColumns = 4 }
        )
        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val season = item as? Season ?: return@OnItemViewClickedListener
            EpisodesActivity.start(requireContext(), media, season.number)
        }

        if (savedInstanceState == null) viewModel.load(media)
        observe()
    }

    private fun observe() {
        viewLifecycleOwnerLiveData.observe(this) { owner ->
            owner ?: return@observe
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.seasons.collect { rowsAdapter.setItems(it, null) }
                }
            }
        }
    }

    companion object {
        private const val ARG_MEDIA = "media"
        fun newInstance(media: MediaItem) = SeasonsFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_MEDIA, media) }
        }
    }
}

/** Renders a [Season] as a poster ImageCardView. */
private class SeasonPresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions((180 * resources.displayMetrics.density).toInt(),
                                   (270 * resources.displayMetrics.density).toInt())
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val s = item as? Season ?: return
        val card = viewHolder.view as ImageCardView
        card.titleText = s.name
        card.contentText = "${s.episodeCount} episodes"
        s.posterUrl?.let { card.mainImageView?.load(it) {
            placeholder(R.drawable.placeholder_poster)
            error(R.drawable.placeholder_poster)
            crossfade(false)
        } } ?: card.mainImageView?.setImageResource(R.drawable.placeholder_poster)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as ImageCardView).mainImage = null
    }
}

class SeasonsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TmdbRepository.get(application)
    private val _seasons = MutableStateFlow<List<Season>>(emptyList())
    val seasons: StateFlow<List<Season>> = _seasons.asStateFlow()

    fun load(media: MediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _seasons.value = repo.seasonsOf(media)
            } catch (e: Exception) {
                android.util.Log.w("SeasonsViewModel", "load failed", e)
            }
        }
    }
}
