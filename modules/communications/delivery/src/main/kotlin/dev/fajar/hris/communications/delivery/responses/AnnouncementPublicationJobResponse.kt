package dev.fajar.hris.communications.delivery.responses

import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.util.UUID

data class AnnouncementPublicationJobResponse(
    val id: UUID,
    val status: JobStatus,
    val cancellationRequested: Boolean,
    val version: Long,
    val failureCode: String?,
)
