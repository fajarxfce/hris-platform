package dev.fajar.hris.jobs.delivery.responses

import java.util.UUID

data class JobResponse(
    val id: UUID,
    val kind: String,
    val status: String,
    val completedItems: Int,
    val totalItems: Int,
    val attempts: Int,
    val cancellationRequested: Boolean,
    val failureCode: String?,
    val createdAt: String,
    val finishedAt: String?,
    val version: Long,
    val availableActions: List<String>,
)
