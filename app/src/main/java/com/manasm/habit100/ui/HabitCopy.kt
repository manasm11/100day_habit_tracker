package com.manasm.habit100.ui

import com.manasm.habit100.domain.HabitKind

/**
 * Every user-facing string that differs between a habit you are building and one you are
 * quitting (§15). The mechanics are identical — 100 days, 10 misses, never two in a row — so
 * only the wording changes, and it changes in one place.
 */
class HabitCopy(val kind: HabitKind) {

    private val quit get() = kind == HabitKind.QUIT

    // ---- New habit

    val namePrompt: String =
        if (quit) "What will you stay away from?" else "What will you do daily?"

    val rulesBlurb: String = if (quit) {
        "One habit at a time. 100 days clean. Up to 10 slips — but never two days in a row."
    } else {
        "One habit at a time. 100 days. Up to 10 misses — but never two days in a row."
    }

    val startButton: String = if (quit) "Start 100 days clean" else "Start 100 days"

    // ---- Tracker

    val doneStatLabel: String = if (quit) "Clean days" else "Completed"
    val missStatLabel: String = if (quit) "Slips left" else "Misses left"
    val doneLegend: String = if (quit) "clean" else "done"
    val missedLegend: String = if (quit) "slipped" else "missed"
    val undoMarkLabel: String =
        if (quit) "Undo — I didn't actually stay clean" else "Undo — I didn't actually do it"

    /** The primary button: record the day as a good one. */
    fun markButton(dayWord: String, alreadyMarked: Boolean): String = when {
        alreadyMarked && quit -> "Stayed clean $dayWord ✓"
        alreadyMarked -> "Marked $dayWord ✓"
        quit -> "I stayed clean $dayWord"
        dayWord == "yesterday" -> "Mark yesterday done"
        else -> "Mark today done"
    }

    fun slipButton(dayWord: String): String = "I slipped $dayWord"

    fun atRiskTitle(dayWord: String): String =
        if (quit) "Don't slip $dayWord" else "Don't miss $dayWord"

    /** [previousWord] is what to call the day before the one in play. */
    fun atRiskBody(previousWord: String): String = if (quit) {
        "You slipped $previousWord. Slip again and the attempt ends — two in a row."
    } else {
        "You missed $previousWord. Miss again and the attempt fails — two in a row."
    }

    val graceCardTitle: String = "Yesterday isn't marked yet"

    fun graceCardBody(deadline: String): String = if (quit) {
        "You can still mark it clean until $deadline. After that it locks as a slip."
    } else {
        "You can still mark it done until $deadline. After that it locks as a miss."
    }

    val nothingToMark: String = if (quit) "Nothing to mark right now." else "Nothing to mark right now."
    val comeBackTomorrow: String = "Come back tomorrow."

    // ---- Slip confirmation

    fun slipDialogTitle(dayWord: String): String = "Log a slip for $dayWord?"

    fun slipDialogBody(fatal: Boolean, dayNumber: Int): String = if (fatal) {
        "This is your second slip in a row — it ends the attempt at day $dayNumber, and it " +
            "can't be undone. Only log it if it really happened."
    } else {
        "Day $dayNumber goes down as a slip and spends one of your ten. You can take it back " +
            "until the day rolls over."
    }

    // ---- Trophy / graduation

    fun trophyComplete(trackLength: Int): String =
        if (quit) "$trackLength days clean" else "$trackLength days complete"

    fun trophyHeadline(isTuneUp: Boolean): String = when {
        isTuneUp -> if (quit) "Back on track" else "Tuned back up"
        quit -> "You're free of it"
        else -> "It's a habit now"
    }

    val trophyDaysLabel: String = if (quit) "Clean days" else "Days done"
    val trophyMissLabel: String = if (quit) "Slips used" else "Misses used"

    // ---- Mastered shelf

    val maintenancePrompt: String = if (quit) "Still clean?" else "Still doing this?"

    val slipConfirm: String = "Yes, I slipped"
    val slipDismiss: String = "No — cancel"
    val undoSlipLabel: String = "Undo — I didn't actually slip"

    companion object {
        fun of(kind: HabitKind): HabitCopy = HabitCopy(kind)
    }
}
