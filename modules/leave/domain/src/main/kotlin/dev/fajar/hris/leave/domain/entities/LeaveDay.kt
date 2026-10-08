package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.workforce.domain.entities.CalendarOrigin
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveDay(
    val workDate: LocalDate,
    val portion: LeavePortion,
    val startsAt: Instant,
    val endsAt: Instant,
    val plannedMinutes: Long,
    val chargedMinutes: Long,
    val shiftId: UUID,
    val shiftRevision: Long,
    val scheduleOrigin: CalendarOrigin,
    val scheduleRevision: Long,
)
