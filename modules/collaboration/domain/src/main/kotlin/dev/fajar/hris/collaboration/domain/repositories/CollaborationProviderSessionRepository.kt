package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationProviderSession
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/**
 * Caller owns current authority and transaction, locking account/device before credentials. This
 * repository never grants provider access; retirement requires a revoked device.
 */
interface CollaborationProviderSessionRepository {
    fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationProviderSession?>

    fun create(session: CollaborationProviderSession): Result<Unit>

    fun rotate(session: CollaborationProviderSession, expectedGeneration: Long): Result<Unit>

    /** Clears a revoked device's secret while retaining its terminal generation. */
    fun retire(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedGeneration: Long,
        at: Instant,
    ): Result<Unit>
}
