package com.example.livex.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.player.PlaybackErrors
import com.example.livex.player.PlayerUiState
import com.example.livex.ui.theme.LiveXTheme

/** Accessibility label of the buffering spinner (also how tests find it). */
const val BUFFERING_DESCRIPTION = "Buffering"

private val SpinnerColor = Color(0xFF80CBC4)
private val ErrorAccent = Color(0xFFEF9A9A)
private val Muted = Color(0xFFB0BEC5)

/**
 * Stateless status layer drawn over the video, centered in the area it's given:
 * a spinner while Buffering, an error card with Retry on Error, nothing otherwise.
 */
@Composable
fun PlaybackStatusOverlay(
    playback: PlayerUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (playback) {
            PlayerUiState.Buffering -> BufferingSpinner()
            is PlayerUiState.Error -> ErrorCard(playback, onRetry)
            PlayerUiState.Idle, PlayerUiState.Ready, PlayerUiState.Ended -> Unit
        }
    }
}

/** Drawn with Canvas: tv-material3 has no progress indicator, and this avoids pulling in mobile Material. */
@Composable
private fun BufferingSpinner() {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "spinnerAngle",
    )
    Canvas(
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = BUFFERING_DESCRIPTION },
    ) {
        val strokeWidth = 5.dp.toPx()
        rotate(angle) {
            drawArc(
                color = SpinnerColor,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorCard(error: PlayerUiState.Error, onRetry: () -> Unit) {
    // TV has no touch: move focus to Retry so OK on the remote retries immediately.
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(error) { retryFocus.requestFocus() }

    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Playback error", style = MaterialTheme.typography.titleMedium, color = ErrorAccent)
        Text(error.message, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Text(
            "Code: ${error.code}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = Muted,
        )
        Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) { Text("Retry") }
    }
}

@Preview(widthDp = 500, heightDp = 540, backgroundColor = 0xFF333333, showBackground = true)
@Composable
private fun ErrorPreview() {
    LiveXTheme {
        PlaybackStatusOverlay(
            playback = PlayerUiState.Error(PlaybackErrors.HTTP_404, "ERROR_CODE_IO_BAD_HTTP_STATUS"),
            onRetry = {},
        )
    }
}

@Preview(widthDp = 500, heightDp = 540, backgroundColor = 0xFF333333, showBackground = true)
@Composable
private fun BufferingPreview() {
    LiveXTheme { PlaybackStatusOverlay(playback = PlayerUiState.Buffering, onRetry = {}) }
}
