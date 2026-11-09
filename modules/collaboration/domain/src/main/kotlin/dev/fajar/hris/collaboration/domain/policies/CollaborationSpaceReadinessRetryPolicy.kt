package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceReadinessCheckpoint
import dev.fajar.hris.core.domain.Failure
import java.time.Instant

/** Each cursor owns a finite retry budget; current target admission remains separate. */
fun collaborationSpaceReadinessRetryAt(
    checkpoint: CollaborationSpaceReadinessCheckpoint,
    failure: Failure?,
    now: Instant,
): Instant? {
    if (
        !validCollaborationSpaceReadinessCheckpoint(checkpoint) ||
            checkpoint.attempts >= 8 ||
            now < checkpoint.capturedAt ||
            now >= checkpoint.expiresAt
    )
        return null
    val backoff = 1L shl checkpoint.attempts.coerceIn(1, 8)
    val requested =
        failure?.parameters?.get("retryAfterSeconds")?.toLongOrNull()?.coerceIn(0, 300) ?: 0
    val at = now.plusSeconds(maxOf(backoff, requested))
    return at.takeIf { it < checkpoint.expiresAt }
}
