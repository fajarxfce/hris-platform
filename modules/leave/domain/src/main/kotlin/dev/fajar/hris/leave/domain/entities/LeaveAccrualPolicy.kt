package dev.fajar.hris.leave.domain.entities

data class LeaveAccrualPolicy(
    val frequency: LeaveAccrualFrequency,
    val halfDaysPerPeriod: Int,
    val carryLimitHalfDays: Int,
)
