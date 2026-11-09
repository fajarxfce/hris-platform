package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceKeyPublication
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/**
 * Caller owns transactions and current account/native/device admission. Gateway requests retain
 * their request guards; original transfer retains its provisioning and original-runtime guards.
 */
interface CollaborationSpaceKeyRepository {
    /** Owned facts remain readable after offboarding or native-parent purge. */
    fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationSpaceKeyPublication?>

    /**
     * Pin identity before any provider upload. For an already verified original identity, reserve
     * and acknowledge atomically with its immutable transfer completion.
     */
    fun reserve(value: CollaborationSpaceKeyPublication): Result<Unit>

    /**
     * Only acknowledge the exact reservation after matching signed, currently admitted readback.
     */
    fun publish(expected: CollaborationSpaceKeyPublication, at: Instant): Result<Unit>
}
