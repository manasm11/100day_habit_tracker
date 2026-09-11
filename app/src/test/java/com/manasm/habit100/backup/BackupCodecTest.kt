package com.manasm.habit100.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * §16 — the backup file is the user's own copy of their record, so the codec has to survive
 * the app it was written by. Everything here is pure: no Android, no database.
 */
class BackupCodecTest {

    private fun trophy(name: String = "Read") = BackupHabit(
        name = name,
        kind = null,
        timeZoneId = "America/New_York",
        status = "mastered",
        currentAttempt = 1,
        attemptStartDate = "2026-01-01",
        attemptTrackLength = 100,
        trophyAttempt = 1,
        slipped = false,
        createdAt = 1_767_225_600_000,
        graduatedAt = 1_775_865_600_000,
        failureReason = null,
        failedOnDay = null,
        graduationAcknowledged = true,
        targetKind = "duration",
        targetSeconds = 600,
        targetReps = null,
        dayLogs = (1..100).map {
            BackupDayLog(
                attempt = 1,
                dayNumber = it,
                logDate = LocalDate.of(2026, 1, 1).plusDays((it - 1).toLong()).toString(),
                status = "done",
                markedAt = 1_767_225_600_000 + it * 86_400_000L,
            )
        },
        checkins = listOf(
            BackupCheckin(period = "2026-05", status = "strong", checkedAt = 1_777_000_000_000),
            BackupCheckin(period = "2026-06", status = "slipped", checkedAt = 1_779_000_000_000),
        ),
    )

    private fun forming() = trophy("Smoking").copy(
        kind = "quit",
        status = "forming",
        trophyAttempt = null,
        graduatedAt = null,
        graduationAcknowledged = true,
        targetKind = null,
        targetSeconds = null,
        dayLogs = listOf(
            BackupDayLog(1, 1, "2026-09-01", "done", 1_800_000_000_000),
            BackupDayLog(1, 2, "2026-09-02", "missed", 1_800_086_400_000),
        ),
        checkins = emptyList(),
    )

    private fun file() = BackupFile(
        exportedAt = "2026-09-11T12:00:00Z",
        appVersion = "1.4.0",
        habits = listOf(trophy(), forming()),
    )

    @Test fun a_backup_round_trips_through_json_unchanged() {
        val original = file()
        val decoded = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test fun mastered_habits_keep_their_trophy_their_logs_and_their_checkins() {
        val decoded = BackupCodec.decode(BackupCodec.encode(file()))
        val mastered = decoded.habits.single { it.status == "mastered" }
        assertEquals(1, mastered.trophyAttempt)
        assertEquals(100, mastered.dayLogs.size)
        assertEquals(100, mastered.dayLogs.count { it.status == "done" })
        assertEquals(listOf("2026-05", "2026-06"), mastered.checkins.map { it.period })
        assertEquals("duration", mastered.targetKind)
        assertEquals(600, mastered.targetSeconds)
    }

    @Test fun a_quit_habit_keeps_its_kind_and_its_slip_rows() {
        val decoded = BackupCodec.decode(BackupCodec.encode(file()))
        val quit = decoded.habits.single { it.kind == "quit" }
        assertEquals("Smoking", quit.name)
        assertEquals(1, quit.dayLogs.count { it.status == "missed" })
    }

    @Test fun the_file_declares_its_format_version() {
        val json = BackupCodec.encode(file())
        assertTrue(json.contains("\"format\": ${BackupCodec.FORMAT}"))
        assertEquals(BackupCodec.FORMAT, BackupCodec.decode(json).format)
    }

    @Test fun a_newer_format_is_refused_rather_than_half_read() {
        val json = BackupCodec.encode(file()).replace(
            "\"format\": ${BackupCodec.FORMAT}",
            "\"format\": ${BackupCodec.FORMAT + 1}",
        )
        val e = assertThrowsBackupError { BackupCodec.decode(json) }
        assertTrue(e.message!!, e.message!!.contains("newer version"))
    }

    @Test fun an_older_format_is_still_readable() {
        // Nothing below the current format exists yet, but the door must be open: decoding
        // must key off "format <= FORMAT", not "format == FORMAT".
        assertTrue(BackupCodec.FORMAT >= 1)
        val decoded = BackupCodec.decode(BackupCodec.encode(file().copy(format = 1)))
        assertEquals(1, decoded.format)
    }

    @Test fun garbage_is_refused_with_a_readable_message() {
        listOf("", "   ", "not json at all", "{}", "[1,2,3]", "{\"format\":1}").forEach { junk ->
            val e = assertThrowsBackupError { BackupCodec.decode(junk) }
            assertTrue(
                "unhelpful message for <$junk>: ${e.message}",
                e.message!!.contains("backup", ignoreCase = true),
            )
        }
    }

    @Test fun unknown_fields_from_a_future_app_are_ignored_not_fatal() {
        val json = BackupCodec.encode(file())
            .replaceFirst("\"habits\":", "\"somethingNewInV5\": {\"a\": 1},\n    \"habits\":")
        assertEquals(2, BackupCodec.decode(json).habits.size)
    }

    @Test fun the_json_is_readable_by_a_human() {
        // The user owns this file; it should not be one long line.
        val json = BackupCodec.encode(file())
        assertTrue(json.lines().size > 20)
        assertTrue(json.contains("\"name\": \"Read\""))
    }

    @Test fun an_empty_app_still_produces_a_valid_backup() {
        val empty = BackupFile(exportedAt = "2026-09-11T12:00:00Z", appVersion = "1.4.0", habits = emptyList())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }

    private fun assertThrowsBackupError(block: () -> Unit): BackupError = try {
        block()
        throw AssertionError("expected BackupError")
    } catch (e: BackupError) {
        e
    }
}
