package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class ClosedWorkDayResponse(
    val workDate: LocalDate,
    val fact: String,
    val acceptedMinutes: Long,
    val schedule: ScheduledDayResponse,
    val evidenceIds: List<UUID>,
    val correctionId: UUID?,
    val overtime: List<ApprovedOvertimeResponse>,
)
