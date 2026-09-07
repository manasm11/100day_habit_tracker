package com.manasm.habit100.clock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

private val Context.devClockDataStore: DataStore<Preferences> by preferencesDataStore("dev_clock")
private val OFFSET = longPreferencesKey("offset_seconds")

class DevClockStore(private val context: Context) {
    val offsetSeconds: Flow<Long> = context.devClockDataStore.data.map { it[OFFSET] ?: 0L }
    suspend fun setOffsetSeconds(v: Long) { context.devClockDataStore.edit { it[OFFSET] = v } }
    suspend fun addDays(n: Long) {
        context.devClockDataStore.edit { it[OFFSET] = (it[OFFSET] ?: 0L) + Duration.ofDays(n).seconds }
    }
    suspend fun reset() { context.devClockDataStore.edit { it[OFFSET] = 0L } }
}

/**
 * Wraps a base clock and adds a persisted offset. The offset is cached in memory and
 * refreshed whenever [refresh] is called (the Application observes the store and calls it).
 */
class DevClock(
    private val base: Clock,
    @Volatile private var offsetSeconds: Long = 0L,
) : Clock {
    override fun now(): Instant = base.now().plusSeconds(offsetSeconds)
    fun update(offsetSeconds: Long) { this.offsetSeconds = offsetSeconds }
}
