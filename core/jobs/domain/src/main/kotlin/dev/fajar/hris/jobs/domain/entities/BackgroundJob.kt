package dev.fajar.hris.jobs.domain.entities

import java.time.Instant

data class BackgroundJob(
    val request: JobRequest,
    val status: JobStatus,
    val attempts: Int,
    val cancellationRequested: Boolean,
    val completedItems: Int,
    val checkpoint: Map<String, String>,
    val failureCode: String?,
    val finishedAt: Instant?,
    val version: Long,
    val availableAt: Instant = request.scheduledFor ?: request.createdAt,
)
