package com.example.livex.data

/**
 * Widevine-protected stream: the player asks [licenseUrl] for content keys
 * (MediaItem.DrmConfiguration with C.WIDEVINE_UUID).
 */
data class DrmLicense(val licenseUrl: String)
