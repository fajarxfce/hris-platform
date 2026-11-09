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

    /** Worker acknowledgement runs under the publisher or the exact target account's guards. */
    fun advance(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        next: CollaborationReadinessPosition,
    ): Result<Boolean>

    /** Settlement remains in the original publisher scope, after leaving any target transaction. */
    fun release(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        complete: Boolean,
        availableAt: Instant,
    ): Result<Boolean>
}
