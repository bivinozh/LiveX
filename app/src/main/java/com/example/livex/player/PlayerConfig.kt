package com.example.livex.player

/** The optimizations that can be switched on/off from the guide overlay. */
enum class OptimizationToggle { BUFFER, ABR, NETWORK }

/**
 * Which optimizations the player is currently built with.
 * OkHttp is ON by default (the app's production networking stack); switching it off compares
 * against the platform's DefaultHttpDataSource.
 */
data class PlayerConfig(
    val tunedBuffer: Boolean = false,
    val dataSaverAbr: Boolean = false,
    val okHttpNetwork: Boolean = true,
) {
    fun isEnabled(toggle: OptimizationToggle): Boolean = when (toggle) {
        OptimizationToggle.BUFFER -> tunedBuffer
        OptimizationToggle.ABR -> dataSaverAbr
        OptimizationToggle.NETWORK -> okHttpNetwork
    }

    fun toggled(toggle: OptimizationToggle): PlayerConfig = when (toggle) {
        OptimizationToggle.BUFFER -> copy(tunedBuffer = !tunedBuffer)
        OptimizationToggle.ABR -> copy(dataSaverAbr = !dataSaverAbr)
        OptimizationToggle.NETWORK -> copy(okHttpNetwork = !okHttpNetwork)
    }
}
