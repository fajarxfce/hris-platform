package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceKeyPublication
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Caller owns transactions, current admission and account/native/device/gateway/request guards. */
interface CollaborationSpaceKeyRepository {
    /** Owned facts remain readable after offboarding or native-parent purge. */
    fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationSpaceKeyPublication?>

    /** Commit before provider upload: a lost response must never permit different identity keys. */
    fun reserve(value: CollaborationSpaceKeyPublication): Result<Unit>

    /**
     * Only acknowledge the exact reservation after matching signed, currently admitted readback.
     */
    fun publish(expected: CollaborationSpaceKeyPublication, at: Instant): Result<Unit>
}
