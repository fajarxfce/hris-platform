package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Durable job pointer. Replacing it fences any result from the earlier attempt. */
data class CollaborationProvisioning(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val jobId: UUID,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)
