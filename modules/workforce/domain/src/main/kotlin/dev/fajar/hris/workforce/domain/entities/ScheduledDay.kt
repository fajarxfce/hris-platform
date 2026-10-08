package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface ScheduledDay {
    val workDate: LocalDate

    data class Work(
        override val workDate: LocalDate,
        val shift: ShiftSnapshot,
        val startsAt: Instant,
        val endsAt: Instant,
        val plannedMinutes: Long,
        val origin: CalendarOrigin,
        val originVersion: Long,
    ) : ScheduledDay

    data class Off(
        override val workDate: LocalDate,
        val origin: CalendarOrigin,
        val originVersion: Long,
        val holidayId: UUID? = null,
    ) : ScheduledDay

    data class Unassigned(override val workDate: LocalDate) : ScheduledDay
}
