package dev.fajar.hris.leave.data.models

data class LeavePolicyData(
    val name: String,
    val paid: Boolean,
    val allowPartialDays: Boolean,
    val minServiceMonths: Int,
    val allowedContracts: Set<String>,
    val maxRequestDays: Int,
    val attachmentRequired: Boolean = false,
    val accrual: LeaveAccrualPolicyData? = null,
)
