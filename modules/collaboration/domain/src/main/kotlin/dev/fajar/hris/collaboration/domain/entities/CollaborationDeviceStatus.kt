package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.time.Instant
import java.util.UUID

/** Owned setup metadata only. Never includes a provider token or a participant encryption key. */
data class CollaborationDeviceStatus(
    val id: UUID,
    val version: Long,
    val state: CollaborationDeviceState,
    val platform: CollaborationPlatform,
    val provisioning: CollaborationProvisioningStatus?,
)

data class CollaborationProvisioningStatus(
    val version: Long,
    val jobId: UUID,
    val status: JobStatus,
    val attempts: Int,
    val failureCode: String?,
    val retryNotBefore: Instant?,
)
