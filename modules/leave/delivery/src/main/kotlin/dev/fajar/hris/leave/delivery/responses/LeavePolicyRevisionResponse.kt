package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeavePolicyRevisionResponse(
    val revision: Long,
    val effectiveFrom: LocalDate,
    val name: String,
    val paid: Boolean,
    val allowPartialDays: Boolean,
    val minServiceMonths: Int,
    val allowedContracts: Set<String>,
    val maxRequestDays: Int,
    val active: Boolean,
    val attachmentRequired: Boolean,
    val accrual: LeaveAccrualPolicyResponse?,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
