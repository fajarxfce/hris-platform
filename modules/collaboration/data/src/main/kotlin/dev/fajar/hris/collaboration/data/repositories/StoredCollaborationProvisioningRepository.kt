package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationProvisioningDataSource
import dev.fajar.hris.collaboration.domain.entities.CollaborationProvisioning
import dev.fajar.hris.collaboration.domain.repositories.CollaborationProvisioningRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationDeviceProvisioningRecord
import java.time.ZoneOffset
import java.util.UUID

class StoredCollaborationProvisioningRepository(
    private val source: CollaborationProvisioningDataSource
) : CollaborationProvisioningRepository {
    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationProvisioning?> = safeDatabaseCall {
        source.find(companyId, accountId, deviceId)?.let {
            CollaborationProvisioning(
                it.companyId,
                it.accountId,
                it.deviceId,
                it.jobId,
                it.version,
                it.createdAt.toInstant(),
                it.updatedAt.toInstant(),
            )
        }
    }

    override fun create(value: CollaborationProvisioning): Result<Unit> = safeDatabaseCall {
        source.insert(
            CollaborationDeviceProvisioningRecord().apply {
                companyId = value.companyId
                accountId = value.accountId
                deviceId = value.deviceId
                jobId = value.jobId
                version = value.version
                createdAt = value.createdAt.atOffset(ZoneOffset.UTC)
                updatedAt = value.updatedAt.atOffset(ZoneOffset.UTC)
            }
        )
    }
}
