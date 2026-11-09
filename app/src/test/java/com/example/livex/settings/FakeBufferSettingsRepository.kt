package com.example.livex.settings

import kotlinx.coroutines.flow.MutableStateFlow

class FakeBufferSettingsRepository(initial: BufferSettings = BufferSettings.DEFAULT) : BufferSettingsRepository {
    override val settings = MutableStateFlow(initial)
    val saved = mutableListOf<BufferSettings>()

    override suspend fun save(settings: BufferSettings) {
        saved += settings
        this.settings.value = settings
    }
}
