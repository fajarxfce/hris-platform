package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Owned historical metadata. It supplies no gateway credential or current admission. */
data class CollaborationLegacyGateway(
    val spaceId: UUID,
    val owner: CollaborationLegacyRuntimeOwner,
    val id: UUID,
    val version: Long,
    val createdAt: Instant,
    val rotatedAt: Instant,
    val revokedAt: Instant?,
)
