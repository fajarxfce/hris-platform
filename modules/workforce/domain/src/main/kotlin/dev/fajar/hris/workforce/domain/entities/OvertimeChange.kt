package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.util.UUID

data class OvertimeChange(
    val revision: Long,
    val kind: OvertimeChangeKind,
    val status: OvertimeStatus,
    val actual: OvertimeInterval?,
    val approvedMinutes: Int,
    val approvalId: UUID?,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
