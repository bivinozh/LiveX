package com.example.livex.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface BufferSettingsRepository {
    /** Current settings; emits again whenever they're saved. */
    val settings: Flow<BufferSettings>

    suspend fun save(settings: BufferSettings)
}

/**
 * Preferences DataStore: asynchronous, transactional and Flow-based (unlike SharedPreferences,
 * which can block the main thread). One file, four int keys.
 */
class DataStoreBufferSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BufferSettingsRepository {

    override val settings: Flow<BufferSettings> = dataStore.data
        .map { prefs ->
            val default = BufferSettings.DEFAULT
            BufferSettings(
                minBufferMs = prefs[MIN_BUFFER] ?: default.minBufferMs,
                maxBufferMs = prefs[MAX_BUFFER] ?: default.maxBufferMs,
                bufferForPlaybackMs = prefs[FOR_PLAYBACK] ?: default.bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs = prefs[AFTER_REBUFFER] ?: default.bufferForPlaybackAfterRebufferMs,
            ).normalized() // never hand DefaultLoadControl values it would reject
        }
        .distinctUntilChanged()

    override suspend fun save(settings: BufferSettings) {
        val valid = settings.normalized()
        dataStore.edit { prefs ->
            prefs[MIN_BUFFER] = valid.minBufferMs
            prefs[MAX_BUFFER] = valid.maxBufferMs
            prefs[FOR_PLAYBACK] = valid.bufferForPlaybackMs
            prefs[AFTER_REBUFFER] = valid.bufferForPlaybackAfterRebufferMs
        }
    }

    private companion object {
        val MIN_BUFFER = intPreferencesKey("min_buffer_ms")
        val MAX_BUFFER = intPreferencesKey("max_buffer_ms")
        val FOR_PLAYBACK = intPreferencesKey("buffer_for_playback_ms")
        val AFTER_REBUFFER = intPreferencesKey("buffer_for_playback_after_rebuffer_ms")
    }
}
