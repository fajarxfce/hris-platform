package dev.fajar.hris.leave.delivery.responses

data class LeaveBalanceResponse(
    val year: Int,
    val availableDays: String,
    val reservedDays: String,
    val consumedDays: String,
)
