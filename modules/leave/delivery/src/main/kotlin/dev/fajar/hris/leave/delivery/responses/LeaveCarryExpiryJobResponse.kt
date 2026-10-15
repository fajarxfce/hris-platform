package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.util.UUID

data class LeaveCarryExpiryJobResponse(
    val id: UUID,
    val status: String,
    val completedItems: Int,
    val totalItems: Int,
    val attempts: Int,
    val version: Long,
    val failureCode: String?,
    val scheduledFor: Instant?,
    val availableAt: Instant,
    val finishedAt: Instant?,
)
