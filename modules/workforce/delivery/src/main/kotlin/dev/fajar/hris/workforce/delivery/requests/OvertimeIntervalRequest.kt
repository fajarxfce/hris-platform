package dev.fajar.hris.workforce.delivery.requests

import java.time.Instant

data class OvertimeIntervalRequest(
    val startsAt: Instant,
    val endsAt: Instant,
    val breakMinutes: Int = 0,
)
