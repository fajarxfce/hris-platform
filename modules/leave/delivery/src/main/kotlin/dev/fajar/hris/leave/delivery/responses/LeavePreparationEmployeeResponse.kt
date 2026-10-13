package dev.fajar.hris.leave.delivery.responses

import java.util.UUID

data class LeavePreparationEmployeeResponse(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val status: String,
    val version: Long,
)
