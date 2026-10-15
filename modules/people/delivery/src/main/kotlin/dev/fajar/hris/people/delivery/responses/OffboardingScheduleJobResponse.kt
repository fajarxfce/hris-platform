package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.util.UUID

data class OffboardingScheduleJobResponse(
    val id: UUID,
    val status: String,
    val version: Long,
    val attempts: Int,
    val cancellationRequested: Boolean,
    val completedItems: Int,
    val failureCode: String?,
    val finishedAt: Instant?,
)
