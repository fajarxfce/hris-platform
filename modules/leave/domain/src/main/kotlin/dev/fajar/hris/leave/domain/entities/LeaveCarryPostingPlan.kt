package dev.fajar.hris.leave.domain.entities

/** A calculated movement; the use case assigns its ledger and history identifiers. */
data class LeaveCarryPostingPlan(
    val movement: LeaveCarryMovement,
    val year: Int,
    val expectedVersion: Long,
)
