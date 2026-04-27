package com.scrudio.tv.ui.sources

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scrudio.tv.data.model.MediaItem
import com.scrudio.tv.data.model.MediaType
import com.scrudio.tv.data.model.StreamSource
import com.scrudio.tv.data.repository.SourcesRepository
import com.scrudio.tv.data.repository.TmdbRepository
import com.scrudio.tv.data.settings.ScrudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SourcesViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Loading : UiState
        data object MissingRdKey : UiState
        data object MissingImdbId : UiState
        data class Empty(val reason: String) : UiState
        data class Ready(val sources: List<StreamSource>) : UiState
    }

    private val tmdbRepo = TmdbRepository.get(application)
    private val sourcesRepo = SourcesRepository.get(application)
    private val settings = ScrudioSettings.get(application)

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(media: MediaItem, season: Int, episode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = UiState.Loading

            if (!settings.hasRd()) {
                _state.value = UiState.MissingRdKey
                return@launch
            }

            val imdbId = try {
                tmdbRepo.imdbId(media)
            } catch (e: Exception) {
                Log.w(TAG, "imdbId lookup failed", e)
                null
            }
            if (imdbId.isNullOrBlank()) {
                _state.value = UiState.MissingImdbId
                return@launch
            }

            val sources = sourcesRepo.getStreams(
                imdbId = imdbId,
                type = media.type,
                season = season,
                episode = episode
            )
            _state.value = if (sources.isEmpty()) {
                UiState.Empty("No playable sources for ${media.title}")
            } else {
                UiState.Ready(sources)
            }
        }
    }

    companion object {
        private const val TAG = "SourcesViewModel"
    }
}
