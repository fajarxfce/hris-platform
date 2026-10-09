package dev.fajar.hris.leave.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class LeaveTypeResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val effectiveFrom: LocalDate,
    val paid: Boolean,
    val allowPartialDays: Boolean,
    val minServiceMonths: Int,
    val allowedContracts: Set<String>,
    val maxRequestDays: Int,
    val active: Boolean,
    val version: Long,
    val appliedRevision: Long,
    val attachmentRequired: Boolean,
    val accrual: LeaveAccrualPolicyResponse?,
)
