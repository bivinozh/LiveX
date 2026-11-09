package com.example.livex.manifest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.livex.core.mvi.MviViewModel
import com.example.livex.data.SampleStreams
import com.example.livex.data.manifest.ManifestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManifestViewModel @Inject constructor(
    private val repository: ManifestRepository,
    savedState: SavedStateHandle,
) : MviViewModel<ManifestState, ManifestIntent, ManifestEffect>(initialState(savedState)) {

    private var loadJob: Job? = null

    init {
        load(currentState.currentUrl)
    }

    override fun handleIntent(intent: ManifestIntent) {
        when (intent) {
            ManifestIntent.Refresh -> load(currentState.currentUrl)
            ManifestIntent.SelectMaster -> load(currentState.stream.url)
            is ManifestIntent.SelectVariant ->
                currentState.variants.getOrNull(intent.index)?.let { load(it.url) }
        }
    }

    private fun load(url: String) {
        loadJob?.cancel() // a newer request (e.g. a quick second Refresh) wins
        setState { copy(currentUrl = url, isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val text = repository.fetch(url) // switches to Dispatchers.IO inside the repository
                val isMaster = url == currentState.stream.url
                setState {
                    copy(
                        isLoading = false,
                        text = text,
                        highlights = ManifestAnalyzer.highlights(text),
                        summary = ManifestAnalyzer.summarize(text),
                        // Variants come from the master; keep them while browsing a variant.
                        variants = if (isMaster) ManifestAnalyzer.hlsVariants(text, url) else variants,
                        fetchCount = fetchCount + 1,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message ?: "Download failed") }
            }
        }
    }

    companion object {
        /** Must match the property name in navigation.ManifestRoute. */
        const val KEY_STREAM_ID = "streamId"

        private fun initialState(savedState: SavedStateHandle): ManifestState {
            val stream = SampleStreams.byId(savedState.get<String>(KEY_STREAM_ID)) ?: SampleStreams.all.first()
            return ManifestState(stream = stream, currentUrl = stream.url)
        }
    }
}
