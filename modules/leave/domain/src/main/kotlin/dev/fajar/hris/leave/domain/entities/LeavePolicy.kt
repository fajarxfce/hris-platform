package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.people.domain.entities.ContractKind

data class LeavePolicy(
    val name: String,
    val paid: Boolean,
    val allowPartialDays: Boolean,
    val minServiceMonths: Int,
    val allowedContracts: Set<ContractKind>,
    val maxRequestDays: Int,
    val attachmentRequired: Boolean = false,
    val accrual: LeaveAccrualPolicy? = null,
)
