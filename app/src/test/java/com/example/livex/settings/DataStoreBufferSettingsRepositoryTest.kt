package com.example.livex.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Real DataStore on a temp file: plain JVM, no Android needed. */
class DataStoreBufferSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        // A path that doesn't exist yet (like on a fresh install). Pre-creating it with newFile() makes
        // DataStore's write-then-rename fail on Windows when the target file already exists.
        produceFile = { File(tmp.root, "buffer_settings.preferences_pb") },
    )

    @Test
    fun `nothing saved yet gives the default settings`() = runTest {
        val repository = DataStoreBufferSettingsRepository(dataStore())

        assertEquals(BufferSettings.DEFAULT, repository.settings.first())
    }

    @Test
    fun `saved settings are read back`() = runTest {
        val repository = DataStoreBufferSettingsRepository(dataStore())

        repository.save(BufferPreset.STABLE_VOD.settings)

        assertEquals(BufferPreset.STABLE_VOD.settings, repository.settings.first())
    }

    @Test
    fun `invalid stored values are repaired before reaching the player`() = runTest {
        val store = dataStore()
        store.edit {
            it[intPreferencesKey("min_buffer_ms")] = 1_000
            it[intPreferencesKey("max_buffer_ms")] = 500
            it[intPreferencesKey("buffer_for_playback_ms")] = 3_000
            it[intPreferencesKey("buffer_for_playback_after_rebuffer_ms")] = 4_000
        }

        val settings = DataStoreBufferSettingsRepository(store).settings.first()

        assertEquals(settings.normalized(), settings)
    }
}
