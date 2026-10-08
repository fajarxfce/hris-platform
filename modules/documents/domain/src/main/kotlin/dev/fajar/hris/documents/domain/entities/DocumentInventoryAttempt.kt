package dev.fajar.hris.documents.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.time.Instant
import java.util.UUID

data class DocumentInventoryAttempt(
    val jobId: UUID,
    val number: Int,
    val basePages: Int,
    val actorId: UUID,
    val at: Instant,
    val reason: String,
    val status: JobStatus,
    val failureCode: String?,
)
