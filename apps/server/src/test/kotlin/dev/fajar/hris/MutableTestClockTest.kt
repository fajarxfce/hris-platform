package dev.fajar.hris

import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MutableTestClockTest {
    @Test
    fun explicitRealtimeModeAndFreezingRemainSharedAcrossZoneViews() {
        val frozen = Instant.parse("2026-10-01T16:59:00Z")
        val clock = MutableTestClock(frozen)
        val zoned = clock.withZone(ZoneId.of("Asia/Jakarta"))
        assertEquals(frozen, clock.instant())
        assertEquals(frozen, zoned.instant())
        val before = Instant.now()
        clock.followSystemTime()
        val actual = clock.instant()
        val inZone = zoned.instant()
        val after = Instant.now()
        assertTrue(actual in before..after)
        assertTrue(inZone in before..after)
        assertEquals(ZoneId.of("Asia/Jakarta"), zoned.zone)
        clock.set(frozen.minusSeconds(30))
        assertEquals(frozen.minusSeconds(30), clock.instant())
        assertEquals(clock.instant(), zoned.instant())
    }
}
