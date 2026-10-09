package dev.fajar.hris.leave.delivery.requests

data class LeaveBalanceAdjustmentRequest(
    val days: String,
    val reason: String,
    val expectedVersion: Long,
)
