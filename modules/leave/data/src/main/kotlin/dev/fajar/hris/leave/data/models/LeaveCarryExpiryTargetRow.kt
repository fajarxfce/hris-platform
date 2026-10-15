package dev.fajar.hris.leave.data.models

import java.util.UUID

data class LeaveCarryExpiryTargetRow(
    val companyId: UUID,
    val expiryId: UUID,
    val ordinal: Int,
    val employeeId: UUID,
)
