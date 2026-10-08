package dev.fajar.hris.workforce.data.models

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ScheduledDayData(
    val kind: String,
    val workDate: LocalDate,
    val shift: ShiftSnapshotData? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val plannedMinutes: Long? = null,
    val origin: String? = null,
    val originVersion: Long? = null,
    val holidayId: UUID? = null,
)
