package com.manasm.habit100.ui

import com.manasm.habit100.domain.HabitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one thing a copy table can silently get wrong: a quit habit wearing build-habit
 * words. Every string a user reads on the tracker is asserted to talk about the right thing.
 */
class HabitCopyTest {
    private val build = HabitCopy.of(HabitKind.BUILD)
    private val quit = HabitCopy.of(HabitKind.QUIT)

    @Test fun a_quit_habit_never_says_do_it() {
        val quitStrings = listOf(
            quit.namePrompt,
            quit.rulesBlurb,
            quit.doneStatLabel,
            quit.missStatLabel,
            quit.undoMarkLabel,
            quit.markButton("today", alreadyMarked = false),
            quit.markButton("today", alreadyMarked = true),
            quit.atRiskTitle("today"),
            quit.atRiskBody("yesterday"),
            quit.graceCardBody("10:00 AM"),
            quit.trophyComplete(100),
            quit.trophyHeadline(isTuneUp = false),
            quit.trophyDaysLabel,
            quit.trophyMissLabel,
            quit.maintenancePrompt,
        )
        quitStrings.forEach {
            assertFalse("build-habit wording leaked into a quit habit: \"$it\"", it.contains("miss", ignoreCase = true))
        }
    }

    @Test fun a_build_habit_never_talks_about_slipping() {
        val buildStrings = listOf(
            build.namePrompt,
            build.rulesBlurb,
            build.doneStatLabel,
            build.missStatLabel,
            build.undoMarkLabel,
            build.markButton("today", alreadyMarked = false),
            build.atRiskTitle("today"),
            build.atRiskBody("yesterday"),
            build.graceCardBody("10:00 AM"),
            build.trophyComplete(100),
            build.trophyHeadline(isTuneUp = false),
            build.trophyDaysLabel,
            build.trophyMissLabel,
            build.maintenancePrompt,
        )
        buildStrings.forEach {
            assertFalse("quit wording leaked into a build habit: \"$it\"", it.contains("clean", ignoreCase = true))
            assertFalse("quit wording leaked into a build habit: \"$it\"", it.contains("slip", ignoreCase = true))
        }
    }

    @Test fun the_mark_button_matches_the_day_in_play() {
        assertEquals("Mark today done", build.markButton("today", alreadyMarked = false))
        assertEquals("Mark yesterday done", build.markButton("yesterday", alreadyMarked = false))
        assertEquals("I stayed clean today", quit.markButton("today", alreadyMarked = false))
        assertEquals("I stayed clean yesterday", quit.markButton("yesterday", alreadyMarked = false))
        assertTrue(quit.markButton("today", alreadyMarked = true).endsWith("✓"))
        assertTrue(build.markButton("today", alreadyMarked = true).endsWith("✓"))
    }

    @Test fun the_fatal_slip_dialog_says_it_cannot_be_undone() {
        val fatal = quit.slipDialogBody(fatal = true, dayNumber = 12)
        assertTrue(fatal.contains("ends the attempt"))
        assertTrue(fatal.contains("can't be undone"))
        assertTrue(fatal.contains("day 12"))
    }

    @Test fun the_ordinary_slip_dialog_promises_the_undo() {
        val ordinary = quit.slipDialogBody(fatal = false, dayNumber = 7)
        assertTrue(ordinary.contains("Day 7"))
        assertTrue(ordinary.contains("take it back"))
        assertFalse(ordinary.contains("ends the attempt"))
    }
}
