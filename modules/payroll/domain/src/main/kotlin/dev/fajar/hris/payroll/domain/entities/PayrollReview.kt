package dev.fajar.hris.payroll.domain.entities

import java.time.*
import java.util.UUID

data class PayrollReview(
    val id: UUID,
    val runId: UUID,
    val number: Int,
    val runVersion: Long,
    val approvalId: UUID,
    val referenceDate: LocalDate,
    val totals: PayrollRunTotals,
    val submittedBy: UUID,
    val submittedAt: Instant,
    val reason: String,
    val status: PayrollReviewStatus,
    val version: Long,
)
