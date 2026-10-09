package dev.fajar.hris.leave.delivery.responses

data class LeaveAccrualPolicyResponse(
    val frequency: String,
    val daysPerPeriod: String,
    val carryLimitDays: String,
)
