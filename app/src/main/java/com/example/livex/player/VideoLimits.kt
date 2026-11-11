package com.example.livex.player

import com.example.livex.player.drm.WidevineInfo

/** The largest video rendition ABR may select. Pure, so the combination rules are unit-tested. */
object VideoLimits {

    /**
     * - Data saver (ABR topic): at most 720p.
     * - Protected stream: the device's Widevine cap (SD on L2/L3, none on L1). While the level is still
     *   unknown (it's read off the main thread) assume SD: a too-low cap is lifted a moment later,
     *   a too-high one kills playback with "requested key has not been loaded".
     * Both apply together: the smaller limit wins on each axis.
     */
    fun maxVideoSize(dataSaver: Boolean, isProtected: Boolean, widevine: WidevineInfo?): Pair<Int, Int> {
        var maxWidth = Int.MAX_VALUE
        var maxHeight = Int.MAX_VALUE
        if (dataSaver) {
            maxWidth = AbrProfile.DATA_SAVER_MAX_WIDTH
            maxHeight = AbrProfile.DATA_SAVER_MAX_HEIGHT
        }
        if (isProtected) {
            val drmCap = if (widevine == null) WidevineInfo.SD_HEIGHT else widevine.maxProtectedVideoHeight
            if (drmCap != null) maxHeight = minOf(maxHeight, drmCap)
        }
        return maxWidth to maxHeight
    }
}
