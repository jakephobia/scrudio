package com.scrudio.tv.ui.seasons

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
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
import com.scrudio.tv.data.model.Episode
import com.scrudio.tv.data.model.MediaItem
import com.scrudio.tv.data.repository.TmdbRepository
import com.scrudio.tv.ui.sources.SourcesActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EpisodesActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)
        if (savedInstanceState == null) {
            val media: MediaItem? = intent?.getParcelableExtra(EXTRA_MEDIA)
            val season = intent?.getIntExtra(EXTRA_SEASON, 0) ?: 0
            if (media == null || season <= 0) { finish(); return }
            supportFragmentManager.beginTransaction()
                .replace(R.id.sources_container, EpisodesFragment.newInstance(media, season))
                .commit()
        }
    }

    companion object {
        const val EXTRA_MEDIA = "media"
        const val EXTRA_SEASON = "season"
        fun start(ctx: Context, media: MediaItem, season: Int) {
            ctx.startActivity(
                Intent(ctx, EpisodesActivity::class.java)
                    .putExtra(EXTRA_MEDIA, media)
                    .putExtra(EXTRA_SEASON, season)
            )
        }
    }
}

class EpisodesFragment : VerticalGridSupportFragment() {

    private lateinit var media: MediaItem
    private var season: Int = 0
    private val viewModel: EpisodesViewModel by viewModels()
    private val rowsAdapter = ArrayObjectAdapter(EpisodePresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        media = requireNotNull(arguments?.getParcelable(ARG_MEDIA))
        season = arguments?.getInt(ARG_SEASON, 0) ?: 0
        title = "${media.title} · Season $season"

        setGridPresenter(
            VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL).apply { numberOfColumns = 3 }
        )
        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val ep = item as? Episode ?: return@OnItemViewClickedListener
            SourcesActivity.start(requireContext(), media, ep.seasonNumber, ep.number)
        }

        if (savedInstanceState == null) viewModel.load(media, season)
        viewLifecycleOwnerLiveData.observe(this) { owner ->
            owner ?: return@observe
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.state.collect { render(it) }
                }
            }
        }
    }

    private fun render(state: EpisodesViewModel.UiState) {
        val baseTitle = "${media.title} \u00B7 Season $season"
        when (state) {
            EpisodesViewModel.UiState.Loading -> {
                title = "$baseTitle \u00B7 \u2026"
            }
            EpisodesViewModel.UiState.Empty -> {
                rowsAdapter.clear()
                title = "$baseTitle \u00B7 No episodes"
            }
            is EpisodesViewModel.UiState.Error -> {
                rowsAdapter.clear()
                title = "$baseTitle \u00B7 Error: ${state.message}"
            }
            is EpisodesViewModel.UiState.Ready -> {
                title = "$baseTitle \u00B7 ${state.episodes.size} ep"
                rowsAdapter.setItems(state.episodes, null)
            }
        }
    }

    companion object {
        private const val ARG_MEDIA = "media"
        private const val ARG_SEASON = "season"
        fun newInstance(media: MediaItem, season: Int) = EpisodesFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_MEDIA, media)
                putInt(ARG_SEASON, season)
            }
        }
    }
}

/** Landscape (16:9) card for episodes — uses still images instead of posters. */
private class EpisodePresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions((280 * resources.displayMetrics.density).toInt(),
                                   (158 * resources.displayMetrics.density).toInt())
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val e = item as? Episode ?: return
        val card = viewHolder.view as ImageCardView
        card.titleText = "E${e.number} · ${e.name}"
        card.contentText = if (e.runtimeMinutes != null) "${e.runtimeMinutes} min" else (e.airDate.orEmpty())
        e.stillUrl?.let { card.mainImageView?.load(it) {
            placeholder(R.drawable.placeholder_poster)
            error(R.drawable.placeholder_poster)
            crossfade(false)
        } } ?: card.mainImageView?.setImageResource(R.drawable.placeholder_poster)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as ImageCardView).mainImage = null
    }
}

class EpisodesViewModel(application: Application) : AndroidViewModel(application) {
    sealed interface UiState {
        data object Loading : UiState
        data object Empty : UiState
        data class Error(val message: String) : UiState
        data class Ready(val episodes: List<Episode>) : UiState
    }

    private val repo = TmdbRepository.get(application)
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(media: MediaItem, season: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = UiState.Loading
            try {
                val list = repo.episodesOf(media, season)
                _state.value = if (list.isEmpty()) UiState.Empty else UiState.Ready(list)
            } catch (e: Exception) {
                android.util.Log.w("EpisodesViewModel", "load failed", e)
                _state.value = UiState.Error(e.message ?: e::class.java.simpleName)
            }
        }
    }
}
