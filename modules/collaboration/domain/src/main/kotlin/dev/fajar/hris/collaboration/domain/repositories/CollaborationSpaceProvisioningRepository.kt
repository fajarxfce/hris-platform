package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID

/** Caller owns current admission, transaction/guard order and business receipt/journal writes. */
interface CollaborationSpaceProvisioningRepository {
    fun metadata(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean = false,
    ): Result<CollaborationSpaceProvisioning?>

    fun enqueue(request: CollaborationSpaceProvisioningRequest): Result<Unit>

    fun retry(request: CollaborationSpaceProvisioningRequest, expectedRevision: Long): Result<Unit>

    /** Worker-only bounded metadata claim. Commit this transaction before native admission. */
    fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
    ): Result<List<CollaborationSpaceProvisioning>>

    /** Null failure means success; lease mismatch must roll back any business publication. */
    fun finish(
        lease: CollaborationSpaceProvisioningLease,
        failure: CollaborationSpaceProvisioningFailure? = null,
    ): Result<Boolean>

    fun defer(lease: CollaborationSpaceProvisioningLease, delaySeconds: Int): Result<Boolean>

    fun cancel(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedRevision: Long,
    ): Result<Boolean>
}
