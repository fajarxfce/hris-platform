package dev.fajar.hris.leave.delivery.responses

import java.util.UUID

data class LeaveBalanceSummaryResponse(
    val typeId: UUID,
    val typeCode: String,
    val typeName: String,
    val balance: LeaveBalanceResponse,
)
