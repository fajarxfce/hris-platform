package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollReviewChangeResponse(
    val revision: Long,
    val action: String,
    val status: String,
    val approvalVersion: Long,
    val step: Int?,
    val actorId: UUID,
    val decidingFor: UUID?,
    val reason: String,
    val at: Instant,
)
