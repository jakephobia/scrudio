package com.scrudio.tv.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scrudio.tv.data.model.MediaItem
import com.scrudio.tv.data.repository.FavoritesRepository
import com.scrudio.tv.data.repository.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads the five home rows in parallel from TMDB.
 *
 * Each row is exposed as its own [HomeRow] so the Browse fragment can render
 * partial state (e.g. show "Trending" while "Popular TV" is still loading).
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TmdbRepository.get(application)
    private val favorites = FavoritesRepository.get(application)

    enum class RowKey { FAVORITES, TRENDING, POPULAR_MOVIES, POPULAR_TV, TOP_RATED_MOVIES, TOP_RATED_TV }

    data class HomeRow(
        val key: RowKey,
        val items: List<MediaItem> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null
    )

    private val _rows = MutableStateFlow(
        RowKey.values().associateWith { HomeRow(key = it) }
    )
    val rows: StateFlow<Map<RowKey, HomeRow>> = _rows.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            // Favorites first — instant, no network.
            updateRow(RowKey.FAVORITES, Result.success(favorites.favorites()))

            // Fire TMDB requests concurrently — home becomes interactive faster.
            val trendingDef = async { safeLoad { repo.trending() } }
            val popMoviesDef = async { safeLoad { repo.popularMovies() } }
            val popTvDef = async { safeLoad { repo.popularTv() } }
            val topMoviesDef = async { safeLoad { repo.topRatedMovies() } }
            val topTvDef = async { safeLoad { repo.topRatedTv() } }

            updateRow(RowKey.TRENDING, trendingDef.await())
            updateRow(RowKey.POPULAR_MOVIES, popMoviesDef.await())
            updateRow(RowKey.POPULAR_TV, popTvDef.await())
            updateRow(RowKey.TOP_RATED_MOVIES, topMoviesDef.await())
            updateRow(RowKey.TOP_RATED_TV, topTvDef.await())
        }
    }

    /** Re-read favorites from disk. Called by Home onResume. */
    fun refreshFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            updateRow(RowKey.FAVORITES, Result.success(favorites.favorites()))
        }
    }

    private inline fun safeLoad(block: () -> List<MediaItem>): Result<List<MediaItem>> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            Log.w("MainViewModel", "Row load failed", e)
            Result.failure(e)
        }

    private fun updateRow(key: RowKey, result: Result<List<MediaItem>>) {
        _rows.value = _rows.value.toMutableMap().apply {
            this[key] = HomeRow(
                key = key,
                items = result.getOrDefault(emptyList()),
                loading = false,
                error = result.exceptionOrNull()?.javaClass?.simpleName
            )
        }
    }
}
