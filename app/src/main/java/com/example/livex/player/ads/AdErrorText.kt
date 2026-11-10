package com.example.livex.player.ads

/**
 * Readable ad-error lines. IMA's AdError.toString() prints "AdErrorCode [name: …, number: …]" plus a
 * long message, which floods the overlay. Pure Kotlin (takes the code's name/number), so it's tested.
 *
 * Whatever the code, the outcome is the same: ImaAdsLoader marks the ad as failed and content plays.
 */
object AdErrorText {

    private val explanations = mapOf(
        "VAST_NO_ADS_AFTER_WRAPPER" to "wrapper led to no ad",
        "VAST_TOO_MANY_REDIRECTS" to "too many wrapper redirects",
        "VAST_LOAD_TIMEOUT" to "ad server didn't answer in time",
        "VAST_EMPTY_RESPONSE" to "empty VAST: no ad to show",
        "VAST_MALFORMED_RESPONSE" to "VAST couldn't be parsed",
        "VAST_MEDIA_LOAD_TIMEOUT" to "ad video didn't start in time",
        "VAST_LINEAR_ASSET_MISMATCH" to "no ad file this device can play",
        "VAST_ASSET_NOT_FOUND" to "no ad file this device can play",
        "FAILED_TO_REQUEST_ADS" to "ad request failed (network)",
        "VIDEO_PLAY_ERROR" to "ad video failed to play",
    )

    fun explain(codeName: String): String = explanations[codeName] ?: "ad error"

    /** One overlay line, e.g. "303 VAST_NO_ADS_AFTER_WRAPPER · wrapper led to no ad → skipped". */
    fun overlay(codeName: String, codeNumber: Int): String = "$codeNumber $codeName · ${explain(codeName)} → skipped"

    /** Full line for Logcat, keeping IMA's own message for debugging. */
    fun logcat(codeName: String, codeNumber: Int, message: String?): String =
        "$codeNumber $codeName: ${message.orEmpty().trim().trimEnd('.')}. Ad skipped, content continues."
}
