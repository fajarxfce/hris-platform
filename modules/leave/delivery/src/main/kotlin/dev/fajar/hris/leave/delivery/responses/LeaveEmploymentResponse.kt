package dev.fajar.hris.leave.delivery.responses

import java.util.UUID

data class LeaveEmploymentResponse(
    val id: UUID,
    val employeeNumber: String,
    val status: String,
    val version: Long,
)
