package dev.fajar.hris.workforce.data.models

import java.time.LocalDate
import java.util.UUID

data class ClosedWorkDayData(
    val workDate: LocalDate,
    val fact: String,
    val acceptedMinutes: Long,
    val schedule: ScheduledDayData,
    val evidenceIds: List<UUID>,
    val correctionId: UUID?,
    val overtime: List<ApprovedOvertimeData> = emptyList(),
)
