package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceDeviceDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceDeviceRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceDeviceSessionsRecord
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StoredCollaborationSpaceDeviceRepository(
    private val source: CollaborationSpaceDeviceDataSource
) : CollaborationSpaceDeviceRepository {
    override fun metadata(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): Result<CollaborationSpaceDevice?> = safeDatabaseCall {
        source.find(spaceId, accountId, deviceId, lock)?.let {
            spaceDeviceFromRow(it, spaceId, accountId).also { device ->
                require(device.id == deviceId)
            }
        }
    }

    override fun replace(device: CollaborationSpaceDevice, expectedVersion: Long): Result<Unit> =
        safeDatabaseCall {
                require(expectedVersion in 0..999999 && device.version == expectedVersion + 1)
                val row = spaceDeviceToRow(device)
                spaceDeviceFromRow(row, device.spaceId, device.accountId)
                source.replace(row, expectedVersion)
            }
            .flatMap { changed ->
                if (changed) Result.Success(Unit)
                else Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_device_changed"))
            }

    override fun findBound(
        spaceId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
    ): Result<CollaborationSpaceDevice?> = safeDatabaseCall {
        source.findBound(spaceId, accountId, nativeSessionId)?.let {
            spaceDeviceFromRow(it, spaceId, accountId).also { device ->
                require(device.originNativeSessionId == nativeSessionId)
            }
        }
    }

    override fun otherActiveParents(
        spaceId: UUID,
        accountId: UUID,
        excluding: UUID,
        at: Instant,
    ): Result<Int> =
        safeDatabaseCall {
                source.otherActiveParents(spaceId, accountId, excluding, at)?.also {
                    require(it in 0..6)
                }
            }
            .flatMap { count ->
                if (count == null)
                    Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
                else Result.Success(count)
            }

    override fun register(device: CollaborationSpaceDevice): Result<Unit> = safeDatabaseCall {
        val row = spaceDeviceToRow(device)
        spaceDeviceFromRow(row, device.spaceId, device.accountId)
        require(
            device.state == CollaborationDeviceState.PROVISIONING &&
                device.version == 0L &&
                device.createdAt == device.updatedAt
        )
        source.insertDevice(row)
        source.insertBinding(
            CollaborationSpaceDeviceSessionsRecord().apply {
                spaceId = device.spaceId
                deviceId = device.id
                accountId = device.accountId
                nativeSessionId = device.originNativeSessionId
                boundAt = device.createdAt.atOffset(ZoneOffset.UTC)
            }
        )
    }
}
