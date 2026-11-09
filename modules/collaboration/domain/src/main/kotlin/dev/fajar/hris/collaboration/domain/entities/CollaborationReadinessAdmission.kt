package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant

/** Permission for an opaque wake only, captured under current target guards. */
data class CollaborationReadinessAdmission(
    val deviceVersion: Long,
    val parentVersion: Long,
    val credentialVersion: Long,
    val gatewayVersion: Long,
    val observedAt: Instant,
    val expiresAt: Instant,
)
