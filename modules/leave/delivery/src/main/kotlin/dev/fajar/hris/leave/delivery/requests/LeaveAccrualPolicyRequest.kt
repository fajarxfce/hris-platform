package dev.fajar.hris.leave.delivery.requests

import dev.fajar.hris.leave.domain.entities.LeaveAccrualFrequency

data class LeaveAccrualPolicyRequest(
    val frequency: LeaveAccrualFrequency,
    val daysPerPeriod: String,
    val carryLimitDays: String,
)
