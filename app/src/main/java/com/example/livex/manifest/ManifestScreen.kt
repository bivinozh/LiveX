package com.example.livex.manifest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.data.SampleStreams
import com.example.livex.ui.theme.LiveXTheme
import kotlinx.coroutines.launch

private val CodeBackground = Color(0xFF121212)
private val CodeText = Color(0xFFE0E0E0)
private val Muted = Color(0xFFB0BEC5)
private val ErrorColor = Color(0xFFEF9A9A)

private fun HighlightKind.color(): Color = when (this) {
    HighlightKind.STRUCTURE -> Color(0xFF80CBC4) // teal
    HighlightKind.ATTRIBUTE -> Color(0xFFFFCC80) // amber
    HighlightKind.LIVE -> Color(0xFFF48FB1)      // pink
}

private const val DPAD_SCROLL_PX = 300f

@Composable
fun ManifestScreen(
    modifier: Modifier = Modifier,
    viewModel: ManifestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ManifestContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
fun ManifestContent(
    state: ManifestState,
    onIntent: (ManifestIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header(state)
        Controls(state, onIntent)
        Legend()
        ManifestText(state, Modifier.weight(1f))
    }
}

@Composable
private fun Header(state: ManifestState) {
    Text(
        "${state.stream.title} · ${state.stream.formatLabel}",
        style = MaterialTheme.typography.titleLarge,
    )
    Text(state.currentUrl, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
    val status = when {
        state.error != null -> null
        state.isLoading -> "Downloading…"
        else -> state.summary?.describe()
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    state.error?.let { Text("Download failed: $it", color = ErrorColor) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Controls(state: ManifestState, onIntent: (ManifestIntent) -> Unit) {
    val showVariantPicker = state.variants.isNotEmpty()
    if (!state.stream.isLive && !showVariantPicker) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.stream.isLive) {
            item {
                Button(onClick = { onIntent(ManifestIntent.Refresh) }) { Text("Refresh") }
            }
        }
        if (showVariantPicker) {
            item {
                Button(onClick = { onIntent(ManifestIntent.SelectMaster) }) {
                    Text(if (state.isViewingMaster) "● Master" else "Master")
                }
            }
            itemsIndexed(state.variants) { index, variant ->
                Button(onClick = { onIntent(ManifestIntent.SelectVariant(index)) }) {
                    Text(if (variant.url == state.currentUrl) "● ${variant.label}" else variant.label)
                }
            }
        }
    }
    if (state.stream.isLive) {
        Text("Fetched ${state.fetchCount}×", style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        HighlightKind.entries.forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(kind.color(), RoundedCornerShape(2.dp)))
                Text(kind.legend, style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

/**
 * Plain text can't take D-pad focus, so the scroll area itself is focusable and turns
 * Up/Down into scrolling. At the very top, Up is not consumed so focus can move to the buttons.
 */
@Composable
private fun ManifestText(state: ManifestState, modifier: Modifier = Modifier) {
    val text = state.text ?: return
    val annotated = remember(text, state.highlights) { highlight(text, state.highlights) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CodeBackground, RoundedCornerShape(8.dp))
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        scope.launch { scrollState.animateScrollBy(DPAD_SCROLL_PX) }
                        true
                    }
                    Key.DirectionUp -> if (scrollState.value > 0) {
                        scope.launch { scrollState.animateScrollBy(-DPAD_SCROLL_PX) }
                        true
                    } else {
                        false // let focus move up to the buttons
                    }
                    else -> false
                }
            }
            .focusable()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Text(annotated, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = CodeText)
    }
}

private fun highlight(text: String, highlights: List<Highlight>): AnnotatedString = buildAnnotatedString {
    append(text)
    highlights.forEach { h ->
        addStyle(SpanStyle(color = h.kind.color(), fontWeight = FontWeight.SemiBold), h.start, h.end)
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ManifestContentPreview() {
    val text = """
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:1234
        #EXTINF:6.000,
        seg1234.ts
    """.trimIndent()
    LiveXTheme {
        ManifestContent(
            state = ManifestState(
                stream = SampleStreams.liveHls,
                currentUrl = SampleStreams.liveHls.url,
                text = text,
                highlights = ManifestAnalyzer.highlights(text),
                summary = ManifestAnalyzer.summarize(text),
                fetchCount = 3,
            ),
            onIntent = {},
        )
    }
}
