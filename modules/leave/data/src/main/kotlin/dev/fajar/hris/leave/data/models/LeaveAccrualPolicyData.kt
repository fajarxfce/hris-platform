package dev.fajar.hris.leave.data.models

import com.fasterxml.jackson.annotation.JsonInclude

data class LeaveAccrualPolicyData(
    val frequency: String,
    val halfDaysPerPeriod: Int,
    val carryLimitHalfDays: Int,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val carryValidityMonths: Int? = null,
)
