package dev.fajar.hris.leave.domain.entities

enum class LeaveBalanceEffect(
    val kind: LeaveLedgerKind,
    val available: Int,
    val reserved: Int,
    val consumed: Int,
) {
    RESERVE(LeaveLedgerKind.RESERVE, -1, 1, 0),
    CONSUME(LeaveLedgerKind.CONSUME, 0, -1, 1),
    RELEASE(LeaveLedgerKind.RELEASE, 1, -1, 0),
    REFUND(LeaveLedgerKind.REFUND, 1, 0, -1),
}
