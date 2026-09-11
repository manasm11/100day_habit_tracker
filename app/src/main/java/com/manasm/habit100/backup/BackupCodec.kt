package com.manasm.habit100.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads and writes [BackupFile] as JSON. Pure — no Android, no database. */
object BackupCodec {

    /**
     * Bumped only when the shape changes in a way an older app cannot read. Decoding accepts
     * anything at or below this number and refuses anything above, rather than silently
     * reading half a file written by a newer version of the app.
     */
    const val FORMAT = 1

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        // A backup written by a future version may carry fields this build has never heard
        // of. Ignoring them is what makes the format readable forward.
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** @throws BackupError when [text] is not a backup this build can read. */
    fun decode(text: String): BackupFile {
        if (text.isBlank()) throw BackupError("That file is empty — it isn't a backup.")

        // Read the version before the body, so a newer file fails with an explanation rather
        // than a deserialization error about some field this build has never seen.
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw BackupError("That file isn't a 100 Day Habit Tracker backup.")
        val format = root["format"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw BackupError("That file isn't a 100 Day Habit Tracker backup.")
        if (format > FORMAT) {
            throw BackupError(
                "That backup was written by a newer version of the app. Update, then restore it.",
            )
        }

        return try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupError("That backup file is damaged and can't be read.", e)
        }
    }
}
