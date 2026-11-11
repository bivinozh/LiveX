package com.example.livex.playback

/** Defaults to a TV (pauses in the background, no PiP), the device the app was first built for. */
data class FakeDeviceCapabilities(
    override val isTelevision: Boolean = true,
    override val supportsPictureInPicture: Boolean = false,
) : DeviceCapabilities {
    companion object {
        val PHONE = FakeDeviceCapabilities(isTelevision = false, supportsPictureInPicture = true)
    }
}
