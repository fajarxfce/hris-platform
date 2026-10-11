package dev.fajar.hris.communications.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.util.UUID

data class AnnouncementPublicationJob(
    val id: UUID,
    val status: JobStatus,
    val cancellationRequested: Boolean,
    val version: Long,
    val failureCode: String?,
)
