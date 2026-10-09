package dev.fajar.hris.leave.domain.entities

data class LeaveBalance(
    val year: Int,
    val availableHalfDays: Int,
    val reservedHalfDays: Int,
    val consumedHalfDays: Int,
    val version: Long = 0,
    val closed: Boolean = false,
    val accountId: java.util.UUID? = null,
)
