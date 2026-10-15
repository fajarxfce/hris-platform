package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

/** One immutable approved basis and its single terminal publication outcome. */
data class PayrollAmendmentPublication(
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
    val status: PayrollAmendmentPublicationStatus = PayrollAmendmentPublicationStatus.PENDING,
    val completedAt: Instant? = null,
    val failureCode: String? = null,
    val completedBy: UUID? = null,
)
