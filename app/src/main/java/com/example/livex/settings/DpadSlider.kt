package com.example.livex.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val Accent = Color(0xFF80CBC4)
private val Muted = Color(0xFFB0BEC5)

fun sliderTag(field: BufferField) = "slider_${field.name}"

/**
 * tv-material3 has no slider, so this is a focusable row the remote drives: Left/Right move one
 * [BufferField.step] (holding the key repeats). Up/Down are not consumed, so focus moves between rows.
 */
@Composable
fun DpadSlider(
    field: BufferField,
    valueMs: Int,
    onValueChange: (Int) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = (valueMs - field.range.first).toFloat() / (field.range.last - field.range.first)

    Column(
        modifier = modifier
            .testTag(sliderTag(field))
            .semantics { contentDescription = "${field.label}: ${field.format(valueMs)}" }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onValueChange((valueMs - field.step).coerceIn(field.range)); true
                    }
                    Key.DirectionRight -> {
                        onValueChange((valueMs + field.step).coerceIn(field.range)); true
                    }
                    else -> false
                }
            }
            .focusable()
            .border(2.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = if (focused) 0.10f else 0.04f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(field.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(
                (if (focused) "◀ " else "") + field.format(valueMs) + (if (focused) " ▶" else ""),
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) Accent else Color.White,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(if (focused) Accent else Muted, RoundedCornerShape(3.dp)),
            )
        }
    }
}
