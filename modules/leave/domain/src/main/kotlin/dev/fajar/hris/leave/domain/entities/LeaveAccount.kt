package dev.fajar.hris.leave.domain.entities

import java.util.UUID

data class LeaveAccount(
    val id: UUID,
    val employeeId: UUID,
    val typeId: UUID,
    val balance: LeaveBalance,
)
