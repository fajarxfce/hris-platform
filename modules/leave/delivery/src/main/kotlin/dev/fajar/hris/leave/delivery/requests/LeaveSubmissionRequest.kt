package dev.fajar.hris.leave.delivery.requests

import java.util.UUID

data class LeaveSubmissionRequest(
    val id: UUID,
    val employeeId: UUID,
    val typeId: UUID,
    val days: List<LeaveDayRequest>,
    val reason: String,
)
