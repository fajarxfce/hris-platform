package dev.fajar.hris.leave.data.models

data class LeaveAccrualPolicyData(
    val frequency: String,
    val halfDaysPerPeriod: Int,
    val carryLimitHalfDays: Int,
)
