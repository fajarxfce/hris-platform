package dev.fajar.hris.workforce.domain.entities

import java.util.UUID

/** Current immutable assignment at an effective date, addressed by its stable sync identity. */
data class WorkScheduleRecord(
    val id: UUID,
    val employeeId: UUID,
    val assignment: ScheduleAssignment,
)
