package dev.fajar.hris.leave.data.models

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveDayData(
    val workDate: LocalDate,
    val portion: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val plannedMinutes: Long,
    val chargedMinutes: Long,
    val shiftId: UUID,
    val shiftRevision: Long,
    val scheduleOrigin: String,
    val scheduleRevision: Long,
)
