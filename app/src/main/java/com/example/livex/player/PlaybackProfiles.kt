package com.example.livex.player

// Buffer durations now live in settings.BufferSettings (editable, saved in DataStore).

/** Track-selection cap used by the "data saver" ABR optimization. */
object AbrProfile {
    const val DATA_SAVER_MAX_WIDTH = 1280
    const val DATA_SAVER_MAX_HEIGHT = 720
}
