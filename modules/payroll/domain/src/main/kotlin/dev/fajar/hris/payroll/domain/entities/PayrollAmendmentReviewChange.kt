package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

data class PayrollAmendmentReviewChange(
    val revision: Long,
    val amendmentRevision: Long,
    val action: PayrollAmendmentAction,
    val status: PayrollAmendmentReviewStatus,
    val approvalVersion: Long,
    val step: Int?,
    val actorId: UUID,
    val decidingFor: UUID?,
    val reason: String,
    val recordedAt: Instant,
)
