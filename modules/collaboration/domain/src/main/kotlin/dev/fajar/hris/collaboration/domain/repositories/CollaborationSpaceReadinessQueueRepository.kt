package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/**
 * The use case owns transactions, target admission and retry policy; neither channel grants content
 * authority.
 */
interface CollaborationSpaceReadinessQueueRepository {
    fun claim(
        channel: CollaborationSpaceReadinessChannel,
        owner: UUID,
        limit: Int,
    ): Result<List<CollaborationSpaceReadinessLease>>

    fun find(
        lease: CollaborationSpaceReadinessLease,
        lock: Boolean,
    ): Result<CollaborationSpaceReadinessCheckpoint?>

    fun targets(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        limit: Int,
    ): Result<List<CollaborationReadinessTarget>>

    fun advance(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        next: CollaborationReadinessPosition,
    ): Result<Boolean>

    fun release(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        complete: Boolean,
        availableAt: Instant,
    ): Result<Boolean>
}
