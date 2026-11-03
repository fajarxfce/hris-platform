package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceProviderSession
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceProviderState
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/**
 * Internal persistence only. The caller owns native/group admission, locks and publication fence.
 */
interface CollaborationSpaceProviderSessionRepository {
    fun metadata(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationSpaceProviderState?>

    /** Decrypt only the exact observed live generation; a changed/retired row is a conflict. */
    fun credential(
        expected: CollaborationSpaceProviderState
    ): Result<CollaborationSpaceProviderSession>

    fun create(session: CollaborationSpaceProviderSession): Result<Unit>

    fun rotate(session: CollaborationSpaceProviderSession, expectedGeneration: Long): Result<Unit>

    /** Requires irreversible device revocation; retained provenance must never be deleted/reset. */
    fun retire(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedGeneration: Long,
        at: Instant,
    ): Result<Unit>
}
