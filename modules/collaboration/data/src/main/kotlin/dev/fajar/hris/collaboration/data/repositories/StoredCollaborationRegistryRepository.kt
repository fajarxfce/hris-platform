package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationRegistryDataSource
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationRegistryRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationDeviceSessionsRecord
import dev.fajar.hris.schema.tables.records.CollaborationDevicesRecord
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StoredCollaborationRegistryRepository(private val store: CollaborationRegistryDataSource) :
    CollaborationRegistryRepository {
    override fun activate(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: Instant,
    ): Result<Boolean> = safeDatabaseCall {
        store.activate(companyId, accountId, deviceId, expectedVersion, at)
    }

    override fun findBound(
        companyId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
    ): Result<CollaborationDevice?> = safeDatabaseCall {
        store.findBound(companyId, accountId, nativeSessionId)?.let { found ->
            val row = found.device
            CollaborationDevice(
                row.id,
                row.companyId,
                row.accountId,
                found.nativeSessionId,
                CollaborationDeviceState.valueOf(row.state),
                row.version,
                row.createdAt.toInstant(),
                row.revokedAt?.toInstant(),
                CollaborationPlatform.valueOf(row.platform),
            )
        }
    }

    override fun countActiveParentSessions(
        accountId: UUID,
        at: Instant,
        excluding: UUID?,
    ): Result<Int> = safeDatabaseCall { store.countActiveParentSessions(accountId, at, excluding) }

    override fun register(registration: CollaborationRegistration): Result<Unit> =
        safeDatabaseCall {
            val time = registration.createdAt.atOffset(ZoneOffset.UTC)
            store.insertDevice(
                CollaborationDevicesRecord().apply {
                    companyId = registration.companyId
                    id = registration.deviceId
                    accountId = registration.accountId
                    platform = registration.platform.name
                    label = registration.label
                    state = CollaborationDeviceState.PROVISIONING.name
                    version = 0
                    createdAt = time
                    updatedAt = time
                    revokedAt = null
                }
            )
            store.insertBinding(
                CollaborationDeviceSessionsRecord().apply {
                    companyId = registration.companyId
                    deviceId = registration.deviceId
                    accountId = registration.accountId
                    nativeSessionId = registration.nativeSessionId
                    boundAt = time
                }
            )
        }
}
