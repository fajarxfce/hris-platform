package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate

data class ScheduleAssignmentResponse(
    val effectiveFrom: LocalDate,
    val revision: Long,
    val days: Map<String, ShiftSnapshotResponse>,
)
