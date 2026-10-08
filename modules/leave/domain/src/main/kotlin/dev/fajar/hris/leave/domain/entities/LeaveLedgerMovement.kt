package dev.fajar.hris.leave.domain.entities

data class LeaveLedgerMovement(
    val year: Int,
    val kind: LeaveLedgerKind,
    val availableDelta: Int,
    val reservedDelta: Int,
    val consumedDelta: Int,
)
