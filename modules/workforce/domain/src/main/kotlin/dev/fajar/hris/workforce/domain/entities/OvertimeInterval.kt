package dev.fajar.hris.workforce.domain.entities

import java.time.Duration
import java.time.Instant

data class OvertimeInterval(val startsAt: Instant, val endsAt: Instant, val breakMinutes: Int) {
    val workedMinutes: Int
        get() = (Duration.between(startsAt, endsAt).toMinutes() - breakMinutes).toInt()
}
