package dev.fajar.hris.leave.delivery.responses

data class LeavePreviewBalanceResponse(
    val year: Int,
    val availableDays: String,
    val reservedDays: String,
    val consumedDays: String,
    val requestedDays: String,
    val remainingDays: String,
    val version: Long,
)
