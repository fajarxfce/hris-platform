package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant

/** An observation of server requests; zero is neither SDK shutdown nor transfer authority. */
data class CollaborationLegacyRuntimeDrainStatus(
    val target: CollaborationLegacyRuntimeTarget,
    val runtimeVersion: Long,
    val activeRequests: Int,
    val latestRequestExpiresAt: Instant?,
    val observedAt: Instant,
)
