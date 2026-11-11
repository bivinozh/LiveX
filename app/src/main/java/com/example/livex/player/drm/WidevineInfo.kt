package com.example.livex.player.drm

import android.media.MediaDrm
import android.util.Log
import androidx.media3.common.C

/**
 * What this device's Widevine CDM can do, read from MediaDrm properties.
 *
 * - L1: decryption AND decoding happen in the TEE (Trusted Execution Environment); decrypted frames
 *   never reach app-accessible memory. Studios require this for HD/UHD.
 * - L2: decryption in the TEE, decoding outside it. Rare.
 * - L3: everything in software. Easier to attack, so license policies usually cap it at SD.
 */
sealed interface WidevineInfo {
    val label: String

    /**
     * Highest video the player should SELECT for protected content, or null for no cap.
     * License servers typically only send HD keys to L1 devices; selecting a rendition whose key the
     * license didn't include fails mid-playback ("requested key has not been loaded").
     */
    val maxProtectedVideoHeight: Int?

    data class Available(
        /** MediaDrm "securityLevel": "L1", "L2" or "L3". */
        val securityLevel: String,
        /** MediaDrm "maxHdcpLevel", e.g. "HDCP-2.2": the output protection the device can enforce. */
        val maxHdcpLevel: String?,
        /** Widevine system id: identifies the device model's CDM certificate (used for revocation). */
        val systemId: String?,
    ) : WidevineInfo {
        val isHardwareSecure: Boolean get() = securityLevel == "L1"

        override val maxProtectedVideoHeight: Int? get() = if (isHardwareSecure) null else SD_HEIGHT

        val hdcpLabel: String get() = maxHdcpLevel ?: "—"

        override val label: String
            get() = when (securityLevel) {
                "L1" -> "Widevine L1 (hardware) · HD/UHD OK"
                "L3" -> "Widevine L3 (software) · usually SD only"
                else -> "Widevine $securityLevel · usually SD only"
            }
    }

    data object Unavailable : WidevineInfo {
        override val label = "No Widevine on this device"
        override val maxProtectedVideoHeight: Int? = null
    }

    companion object {
        const val SD_HEIGHT = 480
    }
}

/** For the stats row: is the current stream protected, and what can the device do. */
data class DrmStatus(val isProtected: Boolean = false, val device: WidevineInfo? = null) {
    val rowText: String
        get() = when {
            !isProtected -> "Clear"
            device is WidevineInfo.Available -> "Widevine ${device.securityLevel}"
            device is WidevineInfo.Unavailable -> "No Widevine"
            else -> "Widevine"
        }
}

/** Reads the Widevine properties. Opening MediaDrm is a binder call into the DRM HAL: call off the main thread. */
object WidevineInfoReader {
    fun read(): WidevineInfo {
        if (!MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)) return WidevineInfo.Unavailable
        return try {
            MediaDrm(C.WIDEVINE_UUID).use { drm ->
                WidevineInfo.Available(
                    securityLevel = drm.getPropertyString("securityLevel"),
                    maxHdcpLevel = runCatching { drm.getPropertyString("maxHdcpLevel") }.getOrNull(),
                    systemId = runCatching { drm.getPropertyString("systemId") }.getOrNull(),
                )
            }
        } catch (e: Exception) {
            Log.w("LiveX-DRM", "Couldn't read Widevine properties", e)
            WidevineInfo.Unavailable
        }
    }
}
