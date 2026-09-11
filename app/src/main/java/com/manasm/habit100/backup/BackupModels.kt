package com.manasm.habit100.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk backup format (§16).
 *
 * Deliberately a plain JSON document rather than a copy of the SQLite file: a `.db` copy is
 * locked to the schema version that wrote it, while this can be read forward across every
 * future migration. The user owns the file, so it is also meant to be readable.
 *
 * Every field is a primitive — dates as ISO strings, instants as epoch millis — so the format
 * never depends on a Room converter or an entity's field order. Day logs and check-ins nest
 * under their habit, which means a restore never has to remap database ids.
 */
@Serializable
data class BackupFile(
    val format: Int = BackupCodec.FORMAT,
    val exportedAt: String,
    val appVersion: String,
    val habits: List<BackupHabit>,
)

@Serializable
data class BackupHabit(
    val name: String,
    val kind: String?,
    val timeZoneId: String,
    val status: String,
    val currentAttempt: Int,
    val attemptStartDate: String,
    val attemptTrackLength: Int,
    val trophyAttempt: Int?,
    val slipped: Boolean,
    val createdAt: Long,
    val graduatedAt: Long?,
    val failureReason: String?,
    val failedOnDay: Int?,
    val graduationAcknowledged: Boolean,
    val targetKind: String?,
    val targetSeconds: Int?,
    val targetReps: Int?,
    @SerialName("dayLogs") val dayLogs: List<BackupDayLog>,
    @SerialName("checkins") val checkins: List<BackupCheckin>,
)

@Serializable
data class BackupDayLog(
    val attempt: Int,
    val dayNumber: Int,
    val logDate: String,
    val status: String,
    val markedAt: Long,
)

@Serializable
data class BackupCheckin(
    val period: String,
    val status: String,
    val checkedAt: Long,
)

/** Anything that makes a file unusable as a backup. The message is shown to the user verbatim. */
class BackupError(message: String, cause: Throwable? = null) : Exception(message, cause)
