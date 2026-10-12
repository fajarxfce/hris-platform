package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant
import java.util.UUID

data class WorkPeriodAttemptResponse(
    val id: UUID,
    val status: String,
    val totalEmployees: Int,
    val completedEmployees: Int,
    val cancellationRequested: Boolean,
    val failureCode: String?,
    val createdAt: Instant,
    val finishedAt: Instant?,
)
