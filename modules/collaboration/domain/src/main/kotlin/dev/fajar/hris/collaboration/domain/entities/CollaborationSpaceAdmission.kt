package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.administration.domain.entities.ClientVersion
import java.time.Instant
import java.util.UUID

/** A short session observation; it grants neither a provider device nor a room membership. */
data class CollaborationSpaceAdmission(
    val spaceId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
    val nativeSessionVersion: Long,
    val credentialVersion: Long,
    val client: ClientVersion,
    val admittedAt: Instant,
    val expiresAt: Instant,
)
