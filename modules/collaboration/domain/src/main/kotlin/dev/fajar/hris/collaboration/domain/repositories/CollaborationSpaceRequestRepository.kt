package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Caller owns current admission, transaction and account/native/device/gateway/slot guards. */
interface CollaborationSpaceRequestRepository {
    fun claim(
        position: CollaborationSpaceRequestPosition,
        until: Instant,
    ): Result<CollaborationSpaceRequestLease?>

    fun find(
        owner: CollaborationSpaceRequestOwner,
        slot: Int,
        lock: Boolean = false,
    ): Result<CollaborationSpaceRequestLease?>

    /**
     * False means the exact lease was already absent/replaced, not that its successor was freed.
     */
    fun release(lease: CollaborationSpaceRequestLease): Result<Boolean>

    /** Finite worker cleanup of expired operational rows; retained device facts are untouched. */
    fun clearExpired(at: Instant, limit: Int): Result<Int>
}
