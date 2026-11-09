package com.example.livex.player

import com.example.livex.settings.AbrSettings
import com.example.livex.settings.BufferSettings

/** The optimizations that can be switched on/off from the guide overlay. */
enum class OptimizationToggle { BUFFER, ABR, NETWORK }

/**
 * Which optimizations the player is currently built with.
 * OkHttp is ON by default (the app's production networking stack); switching it off compares
 * against the platform's DefaultHttpDataSource.
 *
 * Buffer: OFF = Media3's defaults, ON = the user's [buffer] settings (Buffer settings screen).
 */
data class PlayerConfig(
    val tunedBuffer: Boolean = false,
    /** The saved buffer settings (DataStore). Used when [tunedBuffer] is on. */
    val buffer: BufferSettings = BufferSettings.DEFAULT,
    /** AdaptiveTrackSelection.Factory + initial bandwidth estimate (ABR settings screen). Always applied. */
    val abr: AbrSettings = AbrSettings.MEDIA3_DEFAULT,
    val dataSaverAbr: Boolean = false,
    val okHttpNetwork: Boolean = true,
) {
    fun isEnabled(toggle: OptimizationToggle): Boolean = when (toggle) {
        OptimizationToggle.BUFFER -> tunedBuffer
        OptimizationToggle.ABR -> dataSaverAbr
        OptimizationToggle.NETWORK -> okHttpNetwork
    }

    /** What DefaultLoadControl is built with. */
    val effectiveBuffer: BufferSettings get() = if (tunedBuffer) buffer else BufferSettings.MEDIA3_DEFAULT

    fun toggled(toggle: OptimizationToggle): PlayerConfig = when (toggle) {
        OptimizationToggle.BUFFER -> copy(tunedBuffer = !tunedBuffer)
        OptimizationToggle.ABR -> copy(dataSaverAbr = !dataSaverAbr)
        OptimizationToggle.NETWORK -> copy(okHttpNetwork = !okHttpNetwork)
    }
}
