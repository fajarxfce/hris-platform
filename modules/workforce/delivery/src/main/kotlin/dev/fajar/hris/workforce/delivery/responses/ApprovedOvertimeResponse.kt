package dev.fajar.hris.workforce.delivery.responses

import java.util.UUID

data class ApprovedOvertimeResponse(
    val requestId: UUID,
    val revision: Long,
    val actual: OvertimeIntervalResponse,
    val approvedMinutes: Int,
    val schedule: ScheduledDayResponse,
)
