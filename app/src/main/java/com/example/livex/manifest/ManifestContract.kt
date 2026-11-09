package com.example.livex.manifest

import com.example.livex.core.mvi.UiEffect
import com.example.livex.core.mvi.UiIntent
import com.example.livex.core.mvi.UiState
import com.example.livex.data.Stream

data class ManifestState(
    val stream: Stream,
    /** The stream's own URL (master playlist / MPD) or one of its HLS variant playlists. */
    val currentUrl: String,
    val isLoading: Boolean = false,
    /** Last downloaded text; kept while a refresh is in flight so the screen doesn't flash. */
    val text: String? = null,
    val highlights: List<Highlight> = emptyList(),
    val summary: ManifestSummary? = null,
    /** Variants of the HLS master playlist (empty for DASH and for media playlists). */
    val variants: List<HlsVariant> = emptyList(),
    val fetchCount: Int = 0,
    val error: String? = null,
) : UiState {
    val isViewingMaster: Boolean get() = currentUrl == stream.url
}

sealed interface ManifestIntent : UiIntent {
    data object Refresh : ManifestIntent
    data object SelectMaster : ManifestIntent
    data class SelectVariant(val index: Int) : ManifestIntent
}

/** No one-off events yet (Back is handled by Navigation). */
sealed interface ManifestEffect : UiEffect
