package dev.fajar.hris.workforce.data.models

import java.time.Instant

data class OvertimeIntervalData(val startsAt: Instant, val endsAt: Instant, val breakMinutes: Int)
