package dev.fajar.hris.workforce.delivery.responses

import java.util.UUID

data class WorkPeriodSnapshotResponse(
    val employeeId: UUID,
    val month: String,
    val days: List<ClosedWorkDayResponse>,
)
