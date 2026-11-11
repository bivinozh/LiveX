package com.example.livex.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureInPictureTest {

    @Test
    fun `uses the video aspect ratio`() {
        assertEquals(1280 to 720, pipAspectRatio(1280, 720))
        assertEquals(854 to 380, pipAspectRatio(854, 380)) // 2.25:1 scope, still allowed
    }

    @Test
    fun `clamps ratios the system rejects`() {
        assertEquals(239 to 100, pipAspectRatio(3000, 1000)) // 3:1 -> 2.39:1
        assertEquals(100 to 239, pipAspectRatio(500, 2000)) // 1:4 -> 1:2.39
    }

    @Test
    fun `unknown video size falls back to 16 by 9`() {
        assertEquals(16 to 9, pipAspectRatio(0, 0))
    }
}
