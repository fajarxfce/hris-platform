package dev.fajar.hris.approvals.delivery.responses

import java.time.Instant
import java.util.UUID

data class DelegationResponse(
    val id: UUID,
    val kind: String,
    val fromAccount: UUID,
    val toAccount: UUID,
    val validFrom: Instant,
    val validUntil: Instant,
    val active: Boolean,
    val version: Long,
)
