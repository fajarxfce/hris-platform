package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Opaque post-commit wake coordinates. Never a message, key, or content authorization. */
data class CollaborationReadinessControl(
    val eventId: UUID,
    val companyId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
    val deviceId: UUID,
    val publishedAtEpochMillis: Long,
    val publicationRevision: Long,
) {
    override fun toString() = "CollaborationReadinessControl(<redacted>)"
}
