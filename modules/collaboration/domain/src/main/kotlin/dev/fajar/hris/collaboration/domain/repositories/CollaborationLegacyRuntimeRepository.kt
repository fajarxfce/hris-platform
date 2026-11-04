package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Caller owns current admission, account/parent guard order and transaction. */
interface CollaborationLegacyRuntimeRepository {
    /** Insert legacy ownership if absent, then acquire the exact retained row guard. */
    fun ensure(
        owner: CollaborationLegacyRuntimeOwner,
        at: Instant,
    ): Result<CollaborationLegacyRuntime>

    fun find(
        owner: CollaborationLegacyRuntimeOwner,
        lock: Boolean,
    ): Result<CollaborationLegacyRuntime?>

    fun slots(owner: CollaborationLegacyRuntimeOwner): Result<List<CollaborationLegacyRuntimeLease>>

    fun claim(
        owner: CollaborationLegacyRuntimeOwner,
        until: Instant,
    ): Result<CollaborationLegacyRuntimeLease?>

    fun release(lease: CollaborationLegacyRuntimeLease): Result<Boolean>

    fun freeze(
        owner: CollaborationLegacyRuntimeOwner,
        target: CollaborationLegacyRuntimeTarget,
        expectedVersion: Long,
        at: Instant,
    ): Result<Boolean>
}
