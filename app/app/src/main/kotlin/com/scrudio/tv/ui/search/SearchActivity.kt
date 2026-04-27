package com.scrudio.tv.ui.search

import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.scrudio.tv.R
import com.scrudio.tv.data.model.MediaItem
import com.scrudio.tv.data.repository.TmdbRepository
import com.scrudio.tv.ui.cards.CardItem
import com.scrudio.tv.ui.cards.CardPresenter
import com.scrudio.tv.ui.details.DetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.sources_container, ScrudioSearchFragment())
                .commit()
        }
    }
}

/**
 * SearchSupportFragment offers an on-screen TV keyboard, voice (when available)
 * and a results panel. We feed it the same CardPresenter used in the home so
 * the visual style is consistent.
 */
class ScrudioSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val viewModel: SearchViewModel by viewModels()
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val cardPresenter = CardPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            (item as? CardItem)?.media?.let { DetailsActivity.start(requireContext(), it) }
        })

        viewLifecycleOwnerLiveData.observe(this) { owner ->
            owner ?: return@observe
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.results.collect { items -> render(items) }
                }
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String?): Boolean {
        viewModel.queryDebounced(newQuery.orEmpty())
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        viewModel.queryNow(query.orEmpty())
        return true
    }

    private fun render(items: List<MediaItem>) {
        rowsAdapter.clear()
        if (items.isEmpty()) return
        val rowAdapter = ArrayObjectAdapter(cardPresenter).apply {
            items.forEach { add(CardItem.fromMedia(it)) }
        }
        rowsAdapter.add(
            ListRow(HeaderItem(0, getString(R.string.row_search) + " · ${items.size}"), rowAdapter)
        )
    }
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TmdbRepository.get(application)
    private val _results = MutableStateFlow<List<MediaItem>>(emptyList())
    val results: StateFlow<List<MediaItem>> = _results.asStateFlow()

    private var debounceJob: Job? = null
    private var inFlightJob: Job? = null

    /** Re-queries after 350 ms of no input — avoids blasting TMDB per keystroke. */
    fun queryDebounced(text: String) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(350)
            queryNow(text)
        }
    }

    fun queryNow(text: String) {
        inFlightJob?.cancel()
        if (text.isBlank()) {
            _results.value = emptyList()
            return
        }
        inFlightJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _results.value = repo.search(text)
            } catch (e: Exception) {
                android.util.Log.w("SearchViewModel", "search failed", e)
                _results.value = emptyList()
            }
        }
    }
}
