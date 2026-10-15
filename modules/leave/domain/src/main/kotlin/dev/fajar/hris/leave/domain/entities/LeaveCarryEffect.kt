package dev.fajar.hris.leave.domain.entities

/** Unit movements in the carry subledger; the ordinary ledger records their total effect. */
enum class LeaveCarryEffect(
    val available: Int,
    val reserved: Int,
    val consumed: Int,
    val expired: Int = 0,
    val transferred: Int = 0,
    val deducted: Int = 0,
) {
    RESERVE(-1, 1, 0),
    CONSUME(0, -1, 1),
    RELEASE(1, -1, 0),
    REFUND(1, 0, -1),
    EXPIRE(-1, 0, 0, expired = 1),
    TRANSFER(-1, 0, 0, transferred = 1),
    DEDUCT(-1, 0, 0, deducted = 1),
}
