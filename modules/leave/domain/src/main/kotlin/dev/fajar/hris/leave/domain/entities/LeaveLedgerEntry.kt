package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveLedgerEntry(
    val id: UUID,
    val employeeId: UUID,
    val typeId: UUID,
    val year: Int,
    val kind: LeaveLedgerKind,
    val sourceId: UUID,
    val requestId: UUID?,
    val availableDelta: Int,
    val reservedDelta: Int,
    val consumedDelta: Int,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
