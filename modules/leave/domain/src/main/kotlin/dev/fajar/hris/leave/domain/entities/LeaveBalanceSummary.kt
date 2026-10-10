package dev.fajar.hris.leave.domain.entities

import java.util.UUID

data class LeaveBalanceSummary(
    val typeId: UUID,
    val typeCode: String,
    val typeName: String,
    val balance: LeaveBalance,
)
