package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Committed publication metadata only. The caller owns transactions and target scope. */
interface CollaborationReadinessWakeRepository {
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

    /** Must share the same transaction as the target's opaque push append. */
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
