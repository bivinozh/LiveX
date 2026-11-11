package com.example.livex.playback

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What this device can do with playback outside the full-screen player. */
interface DeviceCapabilities {
    /** Android TV / Google TV (leanback). */
    val isTelevision: Boolean

    /** Picture-in-Picture: phones and tablets only here (TV PiP is a different, launcher-driven UX). */
    val supportsPictureInPicture: Boolean

    /**
     * Keep playing when the app leaves the screen. Phones: yes, audio continues with a media
     * notification. TV: no. TV app quality guidelines expect a video app to stop when the user
     * presses Home (nobody listens to a movie with the TV showing the launcher).
     */
    val playsInBackground: Boolean get() = !isTelevision
}

class AndroidDeviceCapabilities @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceCapabilities {
    private val packageManager = context.packageManager

    override val isTelevision: Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    override val supportsPictureInPicture: Boolean =
        !isTelevision && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}
