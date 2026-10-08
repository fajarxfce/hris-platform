package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ScheduledDayResponse(
    val workDate: LocalDate,
    val kind: String,
    val shift: ShiftSnapshotResponse? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val plannedMinutes: Long = 0,
    val origin: String? = null,
    val originVersion: Long? = null,
    val holidayId: UUID? = null,
)
