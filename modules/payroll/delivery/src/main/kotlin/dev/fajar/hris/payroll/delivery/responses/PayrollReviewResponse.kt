package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollReviewResponse(
    val id: UUID,
    val runId: UUID,
    val number: Int,
    val runVersion: Long,
    val approvalId: UUID,
    val referenceDate: LocalDate,
    val totals: PayrollRunTotalsResponse,
    val submittedBy: UUID,
    val submittedAt: Instant,
    val reason: String,
    val status: String,
    val version: Long,
)
