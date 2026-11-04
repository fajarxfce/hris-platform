package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceKeyDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceKeyPublication
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceKeyRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

class StoredCollaborationSpaceKeyRepository(private val source: CollaborationSpaceKeyDataSource) :
    CollaborationSpaceKeyRepository {
    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationSpaceKeyPublication?> = safeDatabaseCall {
        require(listOf(spaceId, accountId, deviceId).none { it == UUID(0, 0) })
        source.find(spaceId, accountId, deviceId)?.toSpaceKey(spaceId, accountId, deviceId)
    }

    override fun reserve(value: CollaborationSpaceKeyPublication): Result<Unit> = safeDatabaseCall {
        require(value.version == 0L)
        source.insert(value.toSpaceKeyRecord())
        require(
            source
                .find(value.spaceId, value.accountId, value.deviceId)
                ?.toSpaceKey(value.spaceId, value.accountId, value.deviceId) == value
        )
    }

    override fun publish(expected: CollaborationSpaceKeyPublication, at: Instant): Result<Unit> =
        safeDatabaseCall {
                require(expected.version == 0L)
                val next = expected.copy(version = 1L, publishedAt = at)
                next.toSpaceKeyRecord()
                val changed = source.publish(expected.toSpaceKeyRecord(), at.atOffset(UTC))
                if (changed)
                    require(
                        source
                            .find(expected.spaceId, expected.accountId, expected.deviceId)
                            ?.toSpaceKey(expected.spaceId, expected.accountId, expected.deviceId) ==
                            next
                    )
                changed
            }
            .flatMap {
                if (it) Result.Success(Unit)
                else
                    Result.Failed(
                        Failure(FailureKind.CONFLICT, "collaboration_space_device_keys_changed")
                    )
            }
}
