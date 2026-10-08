package dev.fajar.hris

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class MutableTestClock
private constructor(private val value: AtomicReference<Instant>, private val zone: ZoneId) :
    Clock() {
    constructor(initial: Instant) : this(AtomicReference(initial), ZoneOffset.UTC)

    fun set(instant: Instant) {
        value.set(instant)
    }

    override fun instant(): Instant = value.get()

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableTestClock(value, zone)
}

@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {
    @Bean @Primary fun testClock() = MutableTestClock(Instant.parse("2026-10-01T16:59:00Z"))
}
