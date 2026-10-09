package dev.fajar.hris.leave.delivery.responses

import java.time.*
import java.util.UUID

data class LeaveAccountResponse(
    val id: UUID,
    val employeeId: UUID,
    val typeId: UUID,
    val year: Int,
    val availableDays: String,
    val reservedDays: String,
    val consumedDays: String,
    val version: Long,
    val closed: Boolean,
)
