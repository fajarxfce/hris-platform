package dev.fajar.hris.workforce.domain.entities

import java.time.YearMonth
import java.util.UUID

data class WorkPeriodSnapshot(
    val employeeId: UUID,
    val month: YearMonth,
    val days: List<ClosedWorkDay>,
)
