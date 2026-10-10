package dev.fajar.hris.leave.delivery.responses

import dev.fajar.hris.core.domain.Page

data class EmployeeLeaveBalancesResponse(
    val employee: LeaveEmployeeReferenceResponse,
    val year: Int,
    val balances: Page<LeaveBalanceSummaryResponse>,
)
