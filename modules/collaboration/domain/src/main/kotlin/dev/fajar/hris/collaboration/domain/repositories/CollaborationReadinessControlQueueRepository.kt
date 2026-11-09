package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/**
 * Independent Matrix publication checkpoint; never FCM progress. The caller owns transactions and
 * target scope.
 */
interface CollaborationReadinessControlQueueRepository {
    fun claim(ownerId: UUID, limit: Int): Result<List<CollaborationReadinessLease>>

    fun find(
        lease: CollaborationReadinessLease,
        lock: Boolean,
    ): Result<CollaborationReadinessCheckpoint?>

    /** Worker-only bounded projection of existing direct peers, through a captured lease. */
    fun targets(
        checkpoint: CollaborationReadinessCheckpoint,
        limit: Int,
    ): Result<List<CollaborationReadinessTarget>>

    /** Fenced acknowledgement after admitted provider delivery, or current target suppression. */
    fun advance(
        checkpoint: CollaborationReadinessCheckpoint,
        next: CollaborationReadinessPosition,
    ): Result<Boolean>

    fun release(
        checkpoint: CollaborationReadinessCheckpoint,
        complete: Boolean,
        availableAt: Instant,
    ): Result<Boolean>
}
