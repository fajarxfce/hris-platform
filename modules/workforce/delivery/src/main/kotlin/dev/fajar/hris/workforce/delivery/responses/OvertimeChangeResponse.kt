package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant
import java.util.UUID

data class OvertimeChangeResponse(
    val revision: Long,
    val kind: String,
    val status: String,
    val actual: OvertimeIntervalResponse?,
    val approvedMinutes: Int,
    val approvalId: UUID?,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
