package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant

data class OvertimeIntervalResponse(
    val startsAt: Instant,
    val endsAt: Instant,
    val breakMinutes: Int,
    val workedMinutes: Int,
)
