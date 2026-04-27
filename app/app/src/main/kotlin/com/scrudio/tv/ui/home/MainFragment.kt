package com.scrudio.tv.ui.home

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.content.Intent
import androidx.leanback.widget.OnItemViewClickedListener
import com.scrudio.tv.R
import com.scrudio.tv.ui.cards.CardItem
import com.scrudio.tv.ui.cards.CardPresenter
import com.scrudio.tv.ui.details.DetailsActivity
import com.scrudio.tv.ui.search.SearchActivity
import com.scrudio.tv.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

/**
 * Home BrowseSupportFragment.
 *
 * Pre-creates one [ListRow] per [MainViewModel.RowKey] with an empty adapter
 * so the headers panel is fully populated immediately. As each TMDB request
 * resolves, [bindRows] updates the matching row's items in-place — Leanback
 * diffs the adapter and re-lays out only the affected row, so the user can
 * already navigate the rows that came back first.
 *
 * D-pad traversal, focus highlights and headers transitions are all handled
 * by Leanback; this class is purely data-binding.
 */
class MainFragment : BrowseSupportFragment() {

    private val viewModel: MainViewModel by viewModels()
    private val cardPresenter = CardPresenter()
    private val rowAdaptersByKey = mutableMapOf<MainViewModel.RowKey, ArrayObjectAdapter>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        brandColor = ContextCompat.getColor(requireContext(), R.color.sage)
        searchAffordanceColor =
            ContextCompat.getColor(requireContext(), R.color.secondary)

        adapter = buildEmptyRowsAdapter()
        observeViewModel()
        wireClicks()

        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up favorites added/removed in DetailsFragment.
        viewModel.refreshFavorites()
    }

    private fun wireClicks() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val card = item as? CardItem ?: return@OnItemViewClickedListener
            when (card.id) {
                CARD_SETTINGS_ID -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
                else -> card.media?.let { DetailsActivity.start(requireContext(), it) }
            }
        }
    }

    private fun buildEmptyRowsAdapter(): ArrayObjectAdapter {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val titles = mapOf(
            MainViewModel.RowKey.FAVORITES to R.string.row_favorites,
            MainViewModel.RowKey.TRENDING to R.string.row_trending,
            MainViewModel.RowKey.POPULAR_MOVIES to R.string.row_popular_movies,
            MainViewModel.RowKey.POPULAR_TV to R.string.row_popular_tv,
            MainViewModel.RowKey.TOP_RATED_MOVIES to R.string.row_top_rated_movies,
            MainViewModel.RowKey.TOP_RATED_TV to R.string.row_top_rated_tv
        )
        titles.entries.forEachIndexed { index, (key, resId) ->
            val rowAdapter = ArrayObjectAdapter(cardPresenter)
            rowAdaptersByKey[key] = rowAdapter
            rowsAdapter.add(ListRow(HeaderItem(index.toLong(), getString(resId)), rowAdapter))
        }

        // ── "Settings" pseudo-row at the bottom ──────────────────────────
        val settingsAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(
                CardItem(
                    id = CARD_SETTINGS_ID,
                    title = getString(R.string.settings_title),
                    subtitle = getString(R.string.settings_rd_key_title),
                    posterUrl = "android.resource://${requireContext().packageName}/${R.drawable.ic_settings}"
                )
            )
        }
        rowsAdapter.add(
            ListRow(
                HeaderItem(titles.size.toLong(), getString(R.string.settings_title)),
                settingsAdapter
            )
        )
        return rowsAdapter
    }

    companion object {
        private const val CARD_SETTINGS_ID = -1L
    }

    private fun observeViewModel() {
        viewLifecycleOwnerLiveData.observe(this) { owner ->
            owner ?: return@observe
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.rows.collect { state -> bindRows(state) }
                }
            }
        }
    }

    private fun bindRows(state: Map<MainViewModel.RowKey, MainViewModel.HomeRow>) {
        for ((key, row) in state) {
            val adapter = rowAdaptersByKey[key] ?: continue
            val newCards = row.items.map(CardItem.Companion::fromMedia)
            if (sameContents(adapter, newCards)) continue
            adapter.setItems(newCards, null)
        }
    }

    private fun sameContents(adapter: ArrayObjectAdapter, items: List<CardItem>): Boolean {
        if (adapter.size() != items.size) return false
        for (i in 0 until adapter.size()) {
            val existing = adapter[i] as? CardItem ?: return false
            if (existing.id != items[i].id) return false
        }
        return true
    }
}
