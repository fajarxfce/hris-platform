package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollAmendmentPublicationResponse(
    val id: UUID,
    val amendmentId: UUID,
    val reviewId: UUID,
    val calculationId: UUID,
    val amendmentVersion: Long,
    val reviewVersion: Long,
    val approvalVersion: Long,
    val number: Int,
    val jobId: UUID,
    val actorId: UUID,
    val requestedAt: Instant,
    val reason: String,
    val status: String,
    val completedAt: Instant?,
    val failureCode: String?,
    val completedBy: UUID?,
)
