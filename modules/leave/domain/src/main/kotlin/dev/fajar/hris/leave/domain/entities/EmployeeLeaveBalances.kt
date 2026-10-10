package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.core.domain.Page

data class EmployeeLeaveBalances(
    val employee: LeaveEmployeeReference,
    val year: Int,
    val balances: Page<LeaveBalanceSummary>,
)
