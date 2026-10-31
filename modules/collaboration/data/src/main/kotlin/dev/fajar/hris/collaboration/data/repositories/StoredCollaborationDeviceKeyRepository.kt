package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationDeviceKeyDataSource
import dev.fajar.hris.collaboration.domain.entities.CollaborationDeviceKeyPublication
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDeviceKeyRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.CollaborationDeviceKeyPublicationsRecord
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StoredCollaborationDeviceKeyRepository(private val store: CollaborationDeviceKeyDataSource) :
    CollaborationDeviceKeyRepository {
    override fun lock(companyId: UUID, deviceId: UUID): Result<Unit> = safeDatabaseCall {
        store.lock(companyId, deviceId)
    }

    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationDeviceKeyPublication?> = safeDatabaseCall {
        store.find(companyId, accountId, deviceId)?.let {
            CollaborationDeviceKeyPublication(
                it.companyId,
                it.accountId,
                it.deviceId,
                it.fingerprint,
                it.version,
                it.reservedAt.toInstant(),
                it.publishedAt?.toInstant(),
            )
        }
    }

    override fun reserve(value: CollaborationDeviceKeyPublication): Result<Unit> =
        safeDatabaseCall {
            store.insert(
                CollaborationDeviceKeyPublicationsRecord().apply {
                    companyId = value.companyId
                    accountId = value.accountId
                    deviceId = value.deviceId
                    fingerprint = value.fingerprint
                    version = value.version
                    reservedAt = value.reservedAt.atOffset(ZoneOffset.UTC)
                    publishedAt = value.publishedAt?.atOffset(ZoneOffset.UTC)
                }
            )
        }

    override fun publish(value: CollaborationDeviceKeyPublication, at: Instant): Result<Unit> =
        safeDatabaseCall {
                store.publish(
                    value.companyId,
                    value.accountId,
                    value.deviceId,
                    value.fingerprint,
                    value.reservedAt.atOffset(ZoneOffset.UTC),
                    at.atOffset(ZoneOffset.UTC),
                )
            }
            .flatMap {
                if (it) Result.Success(Unit)
                else
                    Result.Failed(
                        Failure(FailureKind.CONFLICT, "collaboration_device_keys_changed")
                    )
            }
}
