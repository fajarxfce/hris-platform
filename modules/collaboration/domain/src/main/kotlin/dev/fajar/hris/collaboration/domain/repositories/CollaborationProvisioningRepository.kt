package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationProvisioning
import dev.fajar.hris.core.domain.Result
import java.util.UUID

/** Caller owns the account/device guards and the same transaction as durable job creation. */
interface CollaborationProvisioningRepository {
    fun find(companyId: UUID, accountId: UUID, deviceId: UUID): Result<CollaborationProvisioning?>

    fun create(value: CollaborationProvisioning): Result<Unit>
}
