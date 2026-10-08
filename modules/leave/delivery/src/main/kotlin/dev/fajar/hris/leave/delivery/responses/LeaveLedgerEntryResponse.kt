package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.util.UUID

data class LeaveLedgerEntryResponse(
    val id: UUID,
    val kind: String,
    val sourceId: UUID,
    val requestId: UUID?,
    val availableDeltaDays: String,
    val reservedDeltaDays: String,
    val consumedDeltaDays: String,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
