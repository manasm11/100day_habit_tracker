package com.manasm.habit100.domain

/**
 * How a habit is done. Persisted on `habits` as three nullable columns
 * (`targetKind`, `targetSeconds`, `targetReps`).
 */
sealed interface HabitTarget {

    data object None : HabitTarget

    /** A countdown of [seconds] (meditation, reading). */
    data class Duration(val seconds: Int) : HabitTarget {
        init { require(seconds > 0) { "duration must be positive" } }
    }

    /** A count to [count] (pushups, squats). */
    data class Reps(val count: Int) : HabitTarget {
        init { require(count > 0) { "reps must be positive" } }
    }

    /** (kind, seconds, reps) column values. */
    fun toColumns(): Triple<String?, Int?, Int?> = when (this) {
        None -> Triple(null, null, null)
        is Duration -> Triple("duration", seconds, null)
        is Reps -> Triple("reps", null, count)
    }

    companion object {
        fun fromColumns(kind: String?, seconds: Int?, reps: Int?): HabitTarget = when {
            kind == "duration" && seconds != null && seconds > 0 -> Duration(seconds)
            kind == "reps" && reps != null && reps > 0 -> Reps(reps)
            else -> None
        }
    }
}
