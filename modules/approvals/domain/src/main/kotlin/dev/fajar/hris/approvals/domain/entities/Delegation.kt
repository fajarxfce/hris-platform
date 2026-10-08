package dev.fajar.hris.approvals.domain.entities

import java.time.Instant
import java.util.UUID

data class Delegation(
    val id: UUID,
    val kind: ApprovalKind,
    val fromAccount: UUID,
    val toAccount: UUID,
    val validFrom: Instant,
    val validUntil: Instant,
    val active: Boolean,
    val version: Long,
)
