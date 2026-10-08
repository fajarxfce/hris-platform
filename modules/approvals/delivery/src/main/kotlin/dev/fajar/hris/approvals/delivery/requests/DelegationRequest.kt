package dev.fajar.hris.approvals.delivery.requests

import dev.fajar.hris.approvals.domain.entities.ApprovalKind
import java.time.Instant
import java.util.UUID

data class DelegationRequest(
    val kind: ApprovalKind,
    val fromAccount: UUID,
    val toAccount: UUID,
    val validFrom: Instant,
    val validUntil: Instant,
    val active: Boolean = true,
    val expectedVersion: Long? = null,
    val reason: String,
)
