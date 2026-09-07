package com.manasm.habit100

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.manasm.habit100.ui.theme.HabitTheme
import org.junit.Rule
import org.junit.Test

/**
 * Documentation of intended on-device Compose coverage for the daily tracker.
 *
 * This file is NOT part of `:app:testDebugUnitTest` and is not run in CI — there is no
 * emulator/device in the build environment. It is kept compiling-plausible so the intent
 * is unambiguous and it can be fleshed out (real [com.manasm.habit100.ui.tracker.TrackerScreen]
 * + a fake/seamed `TrackerViewModel`) once instrumented tests can actually run.
 *
 * Intended assertions against the real screen:
 *  1. "Mark today done" is enabled in `Forming(canMarkToday = true)`; after a click the
 *     button relabels to "Done for today ✓" and becomes disabled, and the helper text
 *     reads "Come back tomorrow."
 *  2. The amber "Don't miss today" banner is shown exactly when the snapshot reports
 *     `atRisk` (missed yesterday, still recoverable), and hidden otherwise.
 *  3. The "dev: +1 day" control is present in debug builds only.
 */
class TrackerScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun markTodayButton_disablesAfterMarking() {
        rule.setContent {
            HabitTheme {
                Surface {
                    // Stand-in for TrackerScreen's Forming content: models the same
                    // "mark once, then locked until tomorrow" interaction the real
                    // ViewModel drives via snapshot.canMarkToday / alreadyDoneToday.
                    var marked by remember { mutableStateOf(false) }
                    Column {
                        Button(onClick = { marked = true }, enabled = !marked) {
                            Text(if (marked) "Done for today ✓" else "Mark today done")
                        }
                        if (marked) Text("Come back tomorrow.")
                    }
                }
            }
        }

        rule.onNodeWithText("Mark today done").performClick()
        rule.onNodeWithText("Done for today ✓").assertIsNotEnabled()
        rule.onNodeWithText("Come back tomorrow.").assertExists()
    }
}
