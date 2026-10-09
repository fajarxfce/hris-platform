package dev.fajar.hris.leave.domain.entities

data class LeaveEntitlements(
    val balance: LeaveBalance,
    val frequency: LeaveAccrualFrequency?,
    val postings: List<LeaveAccrualPosting>,
    val closing: LeaveYearClosing?,
)
