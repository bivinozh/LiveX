package com.example.livex.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.testutil.performDpadClick
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** TV-sized screen: on Robolectric's default phone size the two-column layout collapses. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class)
class BufferSettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sent = mutableListOf<BufferSettingsIntent>()

    private fun setContent(state: BufferSettingsState = BufferSettingsState(isLoading = false)) {
        composeRule.setContent {
            LiveXTheme { BufferSettingsContent(state = state, onIntent = { sent += it }) }
        }
    }

    @Test
    fun showsEverySliderWithItsValue() {
        setContent(BufferSettingsState(isLoading = false, draft = BufferPreset.STABLE_VOD.settings))

        BufferField.entries.forEach { composeRule.onNodeWithText(it.label).assertExists() }
        composeRule.onNodeWithText("120.0 s").assertExists() // max buffer of Stable (VOD)
    }

    @Test
    fun dpadRightAndLeftMoveASliderByOneStep() {
        val draft = BufferPreset.FAST_START.settings
        setContent(BufferSettingsState(isLoading = false, draft = draft))
        val slider = composeRule.onNodeWithTag(sliderTag(BufferField.FOR_PLAYBACK))

        slider.performSemanticsAction(SemanticsActions.RequestFocus)
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        slider.performKeyInput { pressKey(Key.DirectionLeft) }

        val step = BufferField.FOR_PLAYBACK.step
        assertEquals(
            listOf<BufferSettingsIntent>(
                BufferSettingsIntent.Change(BufferField.FOR_PLAYBACK, draft.bufferForPlaybackMs + step),
                BufferSettingsIntent.Change(BufferField.FOR_PLAYBACK, draft.bufferForPlaybackMs - step),
            ),
            sent,
        )
    }

    @Test
    fun presetButtonSendsApplyPreset() {
        setContent()

        composeRule.onNodeWithText(BufferPreset.LOW_MEMORY_TV.label, substring = true).performDpadClick()

        assertEquals(listOf<BufferSettingsIntent>(BufferSettingsIntent.ApplyPreset(BufferPreset.LOW_MEMORY_TV)), sent)
    }

    @Test
    fun saveAndResetSendIntents() {
        setContent(BufferSettingsState(isLoading = false, draft = BufferPreset.FAST_START.settings, saved = BufferSettings.MEDIA3_DEFAULT))

        composeRule.onNodeWithText("Save & apply").performDpadClick()
        composeRule.onNodeWithText("Reset to Media3 default").performDpadClick()

        assertEquals(
            listOf(BufferSettingsIntent.Save, BufferSettingsIntent.ResetToMedia3Default),
            sent,
        )
    }

    @Test
    fun dpadDownFromLastSliderGoesToSave_upFromFirstGoesToPresets() {
        setContent()

        val last = composeRule.onNodeWithTag(sliderTag(BufferField.entries.last()))
        last.performSemanticsAction(SemanticsActions.RequestFocus)
        last.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Save & apply").assertIsFocused()

        val first = composeRule.onNodeWithTag(sliderTag(BufferField.entries.first()))
        first.performSemanticsAction(SemanticsActions.RequestFocus)
        first.performKeyInput { pressKey(Key.DirectionUp) }
        // Exact text: the draft is Fast start, so its description line also contains "Fast start".
        composeRule.onNodeWithText("✓ ${BufferPreset.FAST_START.label}").assertIsFocused()
    }

    @Test
    fun focusedSliderShowsItsWaterTankExplanation() {
        setContent()

        composeRule.onNodeWithTag(sliderTag(BufferField.PLAYBACK_AFTER_REBUFFER))
            .performSemanticsAction(SemanticsActions.RequestFocus)

        composeRule.onNodeWithText(BufferField.PLAYBACK_AFTER_REBUFFER.analogy, substring = true).assertExists()
    }
}
