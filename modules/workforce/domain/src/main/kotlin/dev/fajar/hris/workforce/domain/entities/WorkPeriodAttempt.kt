package dev.fajar.hris.workforce.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.time.Instant
import java.util.UUID

data class WorkPeriodAttempt(
    val id: UUID,
    val initiatedBy: UUID,
    val status: JobStatus,
    val totalEmployees: Int,
    val completedEmployees: Int,
    val cancellationRequested: Boolean,
    val failureCode: String?,
    val createdAt: Instant,
    val finishedAt: Instant?,
)
