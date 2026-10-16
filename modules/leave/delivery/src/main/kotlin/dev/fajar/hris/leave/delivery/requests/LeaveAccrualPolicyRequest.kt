package dev.fajar.hris.leave.delivery.requests

import dev.fajar.hris.leave.domain.entities.LeaveAccrualFrequency
import io.swagger.v3.oas.annotations.media.Schema

data class LeaveAccrualPolicyRequest(
    val frequency: LeaveAccrualFrequency,
    val daysPerPeriod: String,
    val carryLimitDays: String,
    @field:Schema(
        minimum = "1",
        maximum = "12",
        description = "Calendar months from 1 January of the carry year; null adds no deadline.",
    )
    val carryValidityMonths: Int? = null,
)
