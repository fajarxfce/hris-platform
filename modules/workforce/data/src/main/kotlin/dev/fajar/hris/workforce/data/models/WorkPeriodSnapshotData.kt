package dev.fajar.hris.workforce.data.models

import java.util.UUID

data class WorkPeriodSnapshotData(
    val employeeId: UUID,
    val month: String,
    val days: List<ClosedWorkDayData>,
)
