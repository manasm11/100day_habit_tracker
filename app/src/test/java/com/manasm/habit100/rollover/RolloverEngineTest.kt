package com.manasm.habit100.rollover

import com.manasm.habit100.data.HabitEntity
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.FailureReason
import com.manasm.habit100.support.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RolloverEngineTest {
    private val zone = ZoneId.of("America/New_York")
    private val start = LocalDate.of(2026, 1, 1)

    private fun habit(status: String = "forming", trackLength: Int = 100, attempt: Int = 1) =
        HabitEntity(
            id = 7, name = "Read", timeZoneId = zone.id, status = status,
            currentAttempt = attempt, attemptStartDate = start, attemptTrackLength = trackLength,
            trophyAttempt = if (status == "tuning_up") 1 else null, slipped = status == "tuning_up",
            createdAt = Instant.EPOCH, graduatedAt = null, failureReason = null, failedOnDay = null,
        )

    private class FakePort(
        var habit: HabitEntity?,
        val logs: MutableList<DayLog>,
    ) : RolloverPort {
        val inserted = mutableListOf<Int>()
        var failed: Pair<FailureReason, Int>? = null
        var graduated = false
        override suspend fun activeHabit() = habit
        override suspend fun loggedDays(habitId: Long, attempt: Int) = logs.toList()
        override suspend fun insertMissedDays(habitId: Long, attempt: Int, startDate: LocalDate, days: List<Int>, markedAt: Instant) {
            inserted += days; days.forEach { logs += DayLog(it, DayStatus.MISSED) }
        }
        override suspend fun onFailed(habit: HabitEntity, reason: FailureReason, onDay: Int) { failed = reason to onDay; this.habit = null }
        override suspend fun onGraduated(habit: HabitEntity) { graduated = true; this.habit = null }
    }

    private fun clockOnDay(n: Int) =
        FakeClock(start.plusDays((n - 1).toLong()).atTime(12, 0).atZone(zone).toInstant())

    @Test fun fills_elapsed_unmarked_days_in_order() = runTest {
        val port = FakePort(habit(), mutableListOf(DayLog(1, DayStatus.DONE)))
        RolloverEngine(port, clockOnDay(5)).run()   // days 2,3,4 elapsed unmarked
        assertEquals(listOf(2, 3, 4), port.inserted)
        assertEquals(FailureReason.TWO_IN_A_ROW to 3, port.failed)
    }

    @Test fun no_op_when_nothing_elapsed() = runTest {
        val port = FakePort(habit(), mutableListOf(DayLog(1, DayStatus.DONE)))
        RolloverEngine(port, clockOnDay(1)).run()
        assertTrue(port.inserted.isEmpty())
        assertNull(port.failed)
        assertFalse(port.graduated)
    }

    @Test fun idempotent_second_run_does_nothing() = runTest {
        val port = FakePort(habit(), mutableListOf(DayLog(1, DayStatus.DONE)))
        val eng = RolloverEngine(port, clockOnDay(3))
        eng.run()
        val insertedAfterFirst = port.inserted.toList()
        eng.run()
        assertEquals(insertedAfterFirst, port.inserted)
    }

    @Test fun graduates_when_window_complete_clean() = runTest {
        val logs = (1..100).map { DayLog(it, DayStatus.DONE) }.toMutableList()
        val port = FakePort(habit(), logs)
        RolloverEngine(port, clockOnDay(101)).run()
        assertTrue(port.graduated)
    }

    private fun clockAt(calendarDay: Int, hour: Int) =
        FakeClock(start.plusDays((calendarDay - 1).toLong()).atTime(hour, 0).atZone(zone).toInstant())

    @Test fun does_not_materialize_yesterday_as_a_miss_during_the_grace_window() = runTest {
        // calendar day 3, 08:00; day 1 done, day 2 not yet marked -> still inside day 2's grace window
        val port = FakePort(habit(), mutableListOf(DayLog(1, DayStatus.DONE)))
        RolloverEngine(port, clockAt(3, 8)).run()
        assertTrue(port.inserted.isEmpty())
        assertNull(port.failed)
    }

    @Test fun materializes_yesterday_as_a_miss_once_the_grace_window_closes() = runTest {
        // calendar day 3, 11:00; day 2's grace window has closed
        val port = FakePort(habit(), mutableListOf(DayLog(1, DayStatus.DONE)))
        RolloverEngine(port, clockAt(3, 11)).run()
        assertEquals(listOf(2), port.inserted)
    }

    @Test fun a_grace_day_missed_after_the_window_closes_can_fail_two_in_a_row() = runTest {
        // days 1 & 2 never marked; calendar day 3, 11:00 -> both finalize, 2nd consecutive on day 2
        val port = FakePort(habit(), mutableListOf())
        RolloverEngine(port, clockAt(3, 11)).run()
        assertEquals(listOf(1, 2), port.inserted)
        assertEquals(FailureReason.TWO_IN_A_ROW to 2, port.failed)
    }
}
