package com.example.livex.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer

/** Android rejects PiP aspect ratios wider than 2.39:1 or taller than 1:2.39. */
private const val MAX_PIP_RATIO = 2.39f

/** Aspect ratio for the PiP window: the video's, clamped to what the system accepts; 16:9 if unknown. */
internal fun pipAspectRatio(videoWidth: Int, videoHeight: Int): Pair<Int, Int> {
    if (videoWidth <= 0 || videoHeight <= 0) return 16 to 9
    val ratio = videoWidth.toFloat() / videoHeight
    return when {
        ratio > MAX_PIP_RATIO -> 239 to 100
        ratio < 1 / MAX_PIP_RATIO -> 100 to 239
        else -> videoWidth to videoHeight
    }
}

/**
 * Keeps the Activity's PiP params up to date while the player screen is shown.
 *
 * autoEnter (Android 12+): pressing Home or swiping up while the video plays shrinks it into a PiP
 * window by itself, with a smooth animation from [sourceRect] (where the video is on screen).
 * Disabled when not playing or when the screen is left, so Home from the stream list or a paused
 * video doesn't open a PiP window. PiP's play/pause button comes from our MediaSession.
 */
@Composable
fun PictureInPictureEffect(enabled: Boolean, videoWidth: Int, videoHeight: Int, sourceRect: Rect?) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled, videoWidth, videoHeight, sourceRect) {
        val (w, h) = pipAspectRatio(videoWidth, videoHeight)
        activity.setPictureInPictureParams(
            PictureInPictureParams.Builder()
                .setAutoEnterEnabled(enabled)
                .setAspectRatio(Rational(w, h))
                .setSourceRectHint(sourceRect)
                .setSeamlessResizeEnabled(true)
                .build(),
        )
        onDispose {
            activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
        }
    }
}

/** True while the Activity is shown as a PiP window (hide everything but the video then). */
@Composable
fun rememberIsInPictureInPictureMode(): Boolean {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return false
    var inPip by remember(activity) { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
