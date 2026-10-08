package dev.fajar.hris.leave.domain.entities

enum class LeaveLedgerKind {
    ADJUSTMENT,
    GRANT,
    RESERVE,
    CONSUME,
    RELEASE,
    REFUND,
    EXPIRE,
    CARRY_OUT,
    CARRY_IN,
}
