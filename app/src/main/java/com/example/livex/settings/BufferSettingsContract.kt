package com.example.livex.settings

import com.example.livex.core.mvi.UiEffect
import com.example.livex.core.mvi.UiIntent
import com.example.livex.core.mvi.UiState

data class BufferSettingsState(
    val isLoading: Boolean = true,
    /** What's in DataStore (what the player uses). Null until loaded. */
    val saved: BufferSettings? = null,
    /** What the sliders show; only written to DataStore on Save, so the player rebuilds once. */
    val draft: BufferSettings = BufferSettings.DEFAULT,
) : UiState {
    val isDirty: Boolean get() = saved != null && draft != saved
    val matchingPreset: BufferPreset? get() = BufferPreset.matching(draft)
}

sealed interface BufferSettingsIntent : UiIntent {
    data class Change(val field: BufferField, val valueMs: Int) : BufferSettingsIntent
    data class ApplyPreset(val preset: BufferPreset) : BufferSettingsIntent
    data object ResetToMedia3Default : BufferSettingsIntent
    data object Save : BufferSettingsIntent
}

sealed interface BufferSettingsEffect : UiEffect {
    /** Saved to DataStore: the screen goes back, and the player rebuilds with the new LoadControl. */
    data object Saved : BufferSettingsEffect
}
