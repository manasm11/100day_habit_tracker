package com.manasm.habit100.clock

import java.time.Instant

interface Clock { fun now(): Instant }

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
