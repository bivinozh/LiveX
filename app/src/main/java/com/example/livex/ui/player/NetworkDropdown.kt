package com.example.livex.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.livex.network.ThrottleMode
import com.example.livex.player.abr.QualityState

/** Network throttle simulator mode picker. */
@Composable
fun NetworkDropdown(
    mode: ThrottleMode,
    onSelect: (ThrottleMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvDropdown(
        header = "Network: ${mode.label}",
        options = ThrottleMode.entries,
        selected = mode,
        optionLabel = { it.label },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * Quality picker: "Auto (ABR)" plus every available video height (from Player.currentTracks).
 * Auto shows what ABR is currently playing, e.g. "Quality: Auto (720p)".
 */
@Composable
fun QualityDropdown(
    quality: QualityState,
    playingHeight: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val header = when (val selected = quality.selectedHeight) {
        null -> "Quality: Auto" + (playingHeight?.takeIf { it > 0 }?.let { " (${it}p)" } ?: "")
        else -> "Quality: ${selected}p (manual)"
    }
    TvDropdown(
        header = header,
        options = listOf<Int?>(null) + quality.options.map { it.height },
        selected = quality.selectedHeight,
        optionLabel = { height ->
            if (height == null) "Auto (ABR)" else quality.options.first { it.height == height }.label
        },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * D-pad friendly dropdown (tv-material3 has no menu component): a header button that expands an
 * inline list. Opening focuses the current option; choosing one collapses and returns focus to the
 * header, so the remote never loses its place.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> TvDropdown(
    header: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasBeenOpened by remember { mutableStateOf(false) }
    val headerFocus = remember { FocusRequester() }
    val selectedFocus = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) {
            hasBeenOpened = true
            selectedFocus.requestFocus()
        } else if (hasBeenOpened) {
            headerFocus.requestFocus()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = { expanded = !expanded }, modifier = Modifier.focusRequester(headerFocus)) {
            Text("$header ${if (expanded) "▴" else "▾"}")
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Button(
                        onClick = {
                            expanded = false
                            if (!isSelected) onSelect(option)
                        },
                        modifier = if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier,
                    ) {
                        Text(if (isSelected) "✓ ${optionLabel(option)}" else optionLabel(option))
                    }
                }
            }
        }
    }
}
