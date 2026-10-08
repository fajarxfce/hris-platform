package dev.fajar.hris.workforce.delivery.requests

import java.time.DayOfWeek
import java.time.LocalDate

data class WeeklyScheduleRequest(
    val effectiveFrom: LocalDate,
    val days: Map<DayOfWeek, ShiftReferenceRequest>,
    val expectedVersion: Long? = null,
    val reason: String,
)
