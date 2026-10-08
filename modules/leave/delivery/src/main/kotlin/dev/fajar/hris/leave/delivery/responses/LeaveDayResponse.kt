package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveDayResponse(
    val workDate: LocalDate,
    val portion: String,
    val chargedDays: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val plannedMinutes: Long,
    val chargedMinutes: Long,
    val shiftId: UUID,
    val shiftRevision: Long,
    val scheduleOrigin: String,
    val scheduleRevision: Long,
)
