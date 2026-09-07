package com.manasm.habit100.support

import com.manasm.habit100.clock.Clock
import java.time.Duration
import java.time.Instant

class FakeClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
    fun advance(d: Duration) { instant = instant.plus(d) }
    fun advanceDays(n: Long) { instant = instant.plus(Duration.ofDays(n)) }
}
