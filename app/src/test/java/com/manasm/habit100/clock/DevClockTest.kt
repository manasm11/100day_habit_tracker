package com.manasm.habit100.clock

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevClockTest {
    @Test fun offset_shifts_now() {
        val fixed = object : Clock { override fun now() = Instant.ofEpochSecond(1_000) }
        val dev = DevClock(fixed, offsetSeconds = 0)
        assertEquals(Instant.ofEpochSecond(1_000), dev.now())
        dev.update(86_400)
        assertEquals(Instant.ofEpochSecond(87_400), dev.now())
    }

    @Test fun store_add_days_accumulates() = runTest {
        val store = DevClockStore(org.robolectric.RuntimeEnvironment.getApplication())
        store.reset()
        store.addDays(2)
        store.addDays(1)
        val v = store.offsetSeconds.first()
        assertEquals(3L * 86_400, v)
    }
}
