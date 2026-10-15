package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollAmendmentSummaryResponse(
    val id: UUID,
    val firstAssessmentId: UUID,
    val firstMonth: String,
    val employee: PayrollEmployeeResponse,
    val version: Long,
    val status: String,
    val action: String,
    val latestCalculationId: UUID?,
    val createdAt: Instant,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
    val latestReviewId: UUID?,
    val latestPublicationId: UUID?,
)
