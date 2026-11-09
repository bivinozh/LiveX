package com.example.livex.settings

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.core.mvi.CollectEffects
import com.example.livex.ui.theme.LiveXTheme

private val Accent = Color(0xFF80CBC4)
private val Muted = Color(0xFFB0BEC5)
private val Water = Color(0xFF4FC3F7)

/** Colors of the four levels, shared by the tank diagram and its legend. */
private fun BufferField.color(): Color = when (this) {
    BufferField.MAX_BUFFER -> Color(0xFFEF9A9A)
    BufferField.MIN_BUFFER -> Color(0xFFFFCC80)
    BufferField.PLAYBACK_AFTER_REBUFFER -> Color(0xFFCE93D8)
    BufferField.FOR_PLAYBACK -> Color(0xFFA5D6A7)
}

@Composable
fun BufferSettingsScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BufferSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            BufferSettingsEffect.Saved -> {
                Toast.makeText(context, "Saved. The player restarts with the new buffer.", Toast.LENGTH_SHORT).show()
                onSaved()
            }
        }
    }

    BufferSettingsContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BufferSettingsContent(
    state: BufferSettingsState,
    onIntent: (BufferSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…") }
        return
    }

    var focusedField by remember { mutableStateOf(BufferField.MIN_BUFFER) }
    val firstSlider = remember { FocusRequester() }
    val firstPreset = remember { FocusRequester() }
    val saveButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstSlider.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // Left: everything focusable, in ONE vertical path. Left/Right belong to the sliders, so the
        // remote must be able to reach presets and actions with Up/Down only.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Buffer settings", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                "DefaultLoadControl · ▲ ▼ move · ◀ ▶ change a value",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )

            // Presets: a row, so Left/Right moves between them.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BufferPreset.entries.forEachIndexed { index, preset ->
                    Button(
                        onClick = { onIntent(BufferSettingsIntent.ApplyPreset(preset)) },
                        modifier = if (index == 0) Modifier.focusRequester(firstPreset) else Modifier,
                    ) {
                        Text((if (preset == state.matchingPreset) "✓ " else "") + preset.label)
                    }
                }
            }
            Text(
                state.matchingPreset?.let { "${it.label}: ${it.description}" } ?: "Custom values",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )

            BufferField.entries.forEachIndexed { index, field ->
                DpadSlider(
                    field = field,
                    valueMs = field.get(state.draft),
                    onValueChange = { onIntent(BufferSettingsIntent.Change(field, it)) },
                    onFocused = { focusedField = field },
                    // Explicit focus exits: spatial search from a wide slider picks whichever button is
                    // nearest its center (e.g. Reset instead of Save).
                    modifier = when (index) {
                        0 -> Modifier
                            .focusRequester(firstSlider)
                            .focusProperties { up = firstPreset }
                        BufferField.entries.lastIndex -> Modifier.focusProperties { down = saveButton }
                        else -> Modifier
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onIntent(BufferSettingsIntent.Save) },
                    modifier = Modifier.focusRequester(saveButton),
                ) { Text("Save & apply") }
                Button(onClick = { onIntent(BufferSettingsIntent.ResetToMedia3Default) }) {
                    Text("Reset to Media3 default")
                }
            }
            Text(
                if (state.isDirty) "Unsaved changes: the player still uses the saved values." else "Saved values are in use.",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isDirty) Color(0xFFFFCC80) else Muted,
            )
        }

        // Right: read-only. The water tank and the explanation of the focused slider.
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                WaterTank(state.draft)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(BufferField.MAX_BUFFER, BufferField.MIN_BUFFER, BufferField.PLAYBACK_AFTER_REBUFFER, BufferField.FOR_PLAYBACK)
                        .forEach { LegendRow(it, it.get(state.draft)) }
                }
            }
            Explanation(focusedField)
        }
    }
}

@Composable
private fun Explanation(field: BufferField) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("About ${field.label}", style = MaterialTheme.typography.titleSmall, color = field.color())
        Text("🪣 ${field.analogy}", style = MaterialTheme.typography.bodySmall, color = Color.White)
        Text("⚖ ${field.tradeOff}", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun LegendRow(field: BufferField, valueMs: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(field.color(), CircleShape))
        Text(
            "${field.shortName()} ${field.format(valueMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

private fun BufferField.shortName() = when (this) {
    BufferField.MAX_BUFFER -> "Tap closes"
    BufferField.MIN_BUFFER -> "Tap always on below"
    BufferField.PLAYBACK_AFTER_REBUFFER -> "Restart after stall"
    BufferField.FOR_PLAYBACK -> "First start"
}

/** The tank: water filled to maxBuffer, with a line at each of the four levels (scale: 0..max). */
@Composable
private fun WaterTank(settings: BufferSettings) {
    Canvas(
        modifier = Modifier
            .size(width = 90.dp, height = 170.dp)
            .semantics { contentDescription = "Water tank diagram" },
    ) {
        val top = 8.dp.toPx()
        val bodyHeight = size.height - top
        val scaleMax = settings.maxBufferMs.coerceAtLeast(1).toFloat()
        fun levelY(ms: Int) = top + bodyHeight * (1f - ms / scaleMax)

        // Water up to the high-water mark (the most the player will ever buffer).
        drawRect(Water.copy(alpha = 0.25f), topLeft = Offset(0f, levelY(settings.maxBufferMs)), size = Size(size.width, size.height - levelY(settings.maxBufferMs)))
        listOf(BufferField.MAX_BUFFER, BufferField.MIN_BUFFER, BufferField.PLAYBACK_AFTER_REBUFFER, BufferField.FOR_PLAYBACK)
            .forEach { field ->
                val y = levelY(field.get(settings))
                drawLine(field.color(), Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
            }
        drawRect(Color.White.copy(alpha = 0.7f), topLeft = Offset(0f, top), size = Size(size.width, bodyHeight), style = Stroke(2.dp.toPx()))
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun BufferSettingsPreview() {
    LiveXTheme {
        BufferSettingsContent(
            state = BufferSettingsState(isLoading = false, saved = BufferSettings.DEFAULT, draft = BufferPreset.STABLE_VOD.settings),
            onIntent = {},
        )
    }
}
