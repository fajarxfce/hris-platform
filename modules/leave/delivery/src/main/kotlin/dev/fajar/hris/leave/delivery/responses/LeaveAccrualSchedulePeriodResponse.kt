package dev.fajar.hris.leave.delivery.responses

import java.time.Instant

data class LeaveAccrualSchedulePeriodResponse(
    val scheduledFor: Instant,
    val batch: LeaveBatchResponse,
    val jobStatus: String,
    val completedEmployees: Int,
)
