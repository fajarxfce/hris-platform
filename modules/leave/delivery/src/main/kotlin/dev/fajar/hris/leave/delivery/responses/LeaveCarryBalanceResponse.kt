package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveCarryBalanceResponse(
    val closingId: UUID,
    val awardedDays: String,
    val availableDays: String,
    val reservedDays: String,
    val consumedDays: String,
    val expiredDays: String,
    val transferredDays: String,
    val deductedDays: String,
    val validThrough: LocalDate?,
    val expiresAt: Instant?,
    val timezone: String,
    val version: Long,
)
