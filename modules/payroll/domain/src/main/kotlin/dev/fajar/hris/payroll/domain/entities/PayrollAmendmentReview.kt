package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PayrollAmendmentReview(
    val id: UUID,
    val amendmentId: UUID,
    val number: Int,
    val amendmentVersion: Long,
    val calculationId: UUID,
    val approvalId: UUID,
    val referenceDate: LocalDate,
    val timezone: String,
    val exposure: BigDecimal,
    val submittedBy: UUID,
    val submittedAt: Instant,
    val reason: String,
    val status: PayrollAmendmentReviewStatus,
    val version: Long,
)
