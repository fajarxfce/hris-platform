package dev.fajar.hris.people.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.time.Instant
import java.util.UUID

/** Bounded job metadata; the initiating credential and request payload are never delivered. */
data class LifecycleScheduleJob(
    val id: UUID,
    val status: JobStatus,
    val version: Long,
    val attempts: Int,
    val cancellationRequested: Boolean,
    val completedItems: Int,
    val failureCode: String?,
    val finishedAt: Instant?,
)
