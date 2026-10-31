package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** The first provisioning actor remains the provider creator, including uncertain retries. */
data class CollaborationDirectRoomProvisioning(
    val companyId: UUID,
    val conversationId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val jobId: UUID,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Immutable verified provider identity. Reading this metadata does not admit room content. */
data class CollaborationDirectRoomBinding(
    val companyId: UUID,
    val conversationId: UUID,
    val accountId: UUID,
    val jobId: UUID,
    val provisioningVersion: Long,
    val matrixServerName: String,
    val matrixRoomId: String,
    val createdAt: Instant,
)
