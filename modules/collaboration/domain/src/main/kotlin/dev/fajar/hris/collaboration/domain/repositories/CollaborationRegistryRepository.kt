package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** The caller owns the transaction and account guard, including quota checks. */
interface CollaborationRegistryRepository {
    fun findBound(
        companyId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
    ): Result<CollaborationDevice?>

    fun countActiveParentSessions(
        accountId: UUID,
        at: Instant,
        excluding: UUID? = null,
    ): Result<Int>

    fun register(registration: CollaborationRegistration): Result<Unit>

    /**
     * Caller owns current admission and the transaction containing provider storage/job completion.
     */
    fun activate(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: Instant,
    ): Result<Boolean>
}
