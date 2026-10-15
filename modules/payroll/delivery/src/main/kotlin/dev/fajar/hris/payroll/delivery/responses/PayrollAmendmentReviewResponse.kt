package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PayrollAmendmentReviewResponse(
    val id: UUID,
    val amendmentId: UUID,
    val number: Int,
    val amendmentVersion: Long,
    val calculationId: UUID,
    val approvalId: UUID,
    val referenceDate: LocalDate,
    val timezone: String,
    val exposure: String,
    val currency: String,
    val submittedBy: UUID,
    val submittedAt: Instant,
    val reason: String,
    val status: String,
    val version: Long,
)
