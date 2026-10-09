package dev.fajar.hris.workforce.data.models

import java.util.UUID

data class ApprovedOvertimeData(
    val requestId: UUID,
    val revision: Long,
    val actual: OvertimeIntervalData,
    val approvedMinutes: Int,
    val schedule: ScheduledDayData,
)
