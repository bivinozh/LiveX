package com.example.livex.testutil

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey

/** Focuses the node and presses D-pad center, the way a TV remote "clicks" (TV components ignore touch). */
@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.performDpadClick(): SemanticsNodeInteraction {
    performSemanticsAction(SemanticsActions.RequestFocus)
    return performKeyInput { pressKey(Key.DirectionCenter) }
}
