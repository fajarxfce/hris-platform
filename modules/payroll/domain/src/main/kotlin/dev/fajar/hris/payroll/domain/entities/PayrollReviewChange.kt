package dev.fajar.hris.payroll.domain.entities

import java.time.*
import java.util.UUID

data class PayrollReviewChange(
    val revision: Long,
    val action: PayrollReviewAction,
    val status: PayrollReviewStatus,
    val approvalVersion: Long,
    val step: Int?,
    val actorId: UUID,
    val decidingFor: UUID?,
    val reason: String,
    val at: Instant,
)
