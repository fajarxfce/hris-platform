package dev.fajar.hris.workforce.domain.entities

import java.util.UUID

data class ApprovedOvertime(
    val requestId: UUID,
    val revision: Long,
    val actual: OvertimeInterval,
    val approvedMinutes: Int,
    val schedule: ScheduledDay,
)
