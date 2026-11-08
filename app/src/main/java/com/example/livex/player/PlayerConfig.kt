package com.example.livex.player

/** The optimizations that can be switched on/off from the guide overlay. */
enum class OptimizationToggle { BUFFER, ABR, NETWORK }

/** Which optimizations the player is currently built with. All off = Media3 defaults. */
data class PlayerConfig(
    val tunedBuffer: Boolean = false,
    val dataSaverAbr: Boolean = false,
    val okHttpNetwork: Boolean = false,
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
