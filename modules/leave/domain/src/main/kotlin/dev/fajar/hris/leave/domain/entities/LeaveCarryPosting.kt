package dev.fajar.hris.leave.domain.entities

import java.util.UUID

/** One immutable carry movement paired with a main-ledger entry in the same transaction. */
data class LeaveCarryPosting(
    val id: UUID,
    val movement: LeaveCarryMovement,
    val expectedVersion: Long,
    val ledgerEntryId: UUID,
    val requestId: UUID? = null,
)
