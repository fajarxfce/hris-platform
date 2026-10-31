package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationDirectRoomDataSource
import dev.fajar.hris.collaboration.domain.entities.CollaborationDirectRoomBinding
import dev.fajar.hris.collaboration.domain.entities.CollaborationDirectRoomProvisioning
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDirectRoomRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.CollaborationDirectRoomBindingsRecord
import dev.fajar.hris.schema.tables.records.CollaborationDirectRoomProvisioningRecord
import java.time.ZoneOffset
import java.util.UUID

class StoredCollaborationDirectRoomRepository(
    private val store: CollaborationDirectRoomDataSource
) : CollaborationDirectRoomRepository {
    override fun lock(companyId: UUID, conversationId: UUID): Result<Unit> = safeDatabaseCall {
        store.lock(companyId, conversationId)
    }

    override fun provisioning(
        companyId: UUID,
        conversationId: UUID,
    ): Result<CollaborationDirectRoomProvisioning?> = safeDatabaseCall {
        store.provisioning(companyId, conversationId)?.let {
            CollaborationDirectRoomProvisioning(
                it.companyId,
                it.conversationId,
                it.accountId,
                it.deviceId,
                it.jobId,
                it.version,
                it.createdAt.toInstant(),
                it.updatedAt.toInstant(),
            )
        }
    }

    override fun binding(
        companyId: UUID,
        conversationId: UUID,
    ): Result<CollaborationDirectRoomBinding?> = safeDatabaseCall {
        store.binding(companyId, conversationId)?.let {
            CollaborationDirectRoomBinding(
                it.companyId,
                it.conversationId,
                it.accountId,
                it.jobId,
                it.provisioningVersion,
                it.matrixServerName,
                it.matrixRoomId,
                it.createdAt.toInstant(),
            )
        }
    }

    override fun create(value: CollaborationDirectRoomProvisioning): Result<Unit> =
        safeDatabaseCall {
            store.insert(value.record())
        }

    override fun replace(
        value: CollaborationDirectRoomProvisioning,
        expectedVersion: Long,
    ): Result<Unit> =
        safeDatabaseCall { store.replace(value.record(), expectedVersion) }
            .flatMap {
                if (it) Result.Success(Unit)
                else
                    Result.Failed(
                        Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed")
                    )
            }

    override fun publish(value: CollaborationDirectRoomBinding): Result<Unit> = safeDatabaseCall {
        store.publish(
            CollaborationDirectRoomBindingsRecord().apply {
                companyId = value.companyId
                conversationId = value.conversationId
                accountId = value.accountId
                jobId = value.jobId
                provisioningVersion = value.provisioningVersion
                matrixServerName = value.matrixServerName
                matrixRoomId = value.matrixRoomId
                createdAt = value.createdAt.atOffset(ZoneOffset.UTC)
            }
        )
    }

    private fun CollaborationDirectRoomProvisioning.record() =
        CollaborationDirectRoomProvisioningRecord().also {
            it.companyId = companyId
            it.conversationId = conversationId
            it.accountId = accountId
            it.deviceId = deviceId
            it.jobId = jobId
            it.version = version
            it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
            it.updatedAt = updatedAt.atOffset(ZoneOffset.UTC)
        }
}
