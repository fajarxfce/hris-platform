package dev.fajar.hris.leave.domain.entities

data class LeavePreviewBalance(val balance: LeaveBalance, val requestedHalfDays: Int) {
    val remainingHalfDays: Int
        get() = balance.availableHalfDays - requestedHalfDays
}
