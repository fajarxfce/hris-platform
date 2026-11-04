package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRoomRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.time.ZoneOffset.UTC
import java.util.UUID

class StoredCollaborationSpaceRoomRepository(private val source: CollaborationSpaceRoomDataSource) :
    CollaborationSpaceRoomRepository {
    override fun lock(spaceId: UUID, conversationId: UUID): Result<Unit> = safeDatabaseCall {
        requireSpaceRoomIds(spaceId, conversationId)
        source.lock(spaceId, conversationId)
    }

    override fun intent(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): Result<CollaborationSpaceRoomIntent?> = safeDatabaseCall {
        requireSpaceRoomIds(spaceId, accountId, conversationId)
        source
            .intent(spaceId, accountId, conversationId)
            ?.toSpaceRoomIntent(spaceId, accountId, conversationId)
    }

    override fun createIntent(value: CollaborationSpaceRoomIntent): Result<Unit> =
        safeDatabaseCall {
            source.insertIntent(spaceRoomIntentRow(value))
            require(
                source
                    .intent(value.spaceId, value.requestedBy, value.conversationId)
                    ?.toSpaceRoomIntent(value.spaceId, value.requestedBy, value.conversationId) ==
                    value
            )
        }

    override fun binding(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): Result<CollaborationSpaceRoomBinding?> = safeDatabaseCall {
        requireSpaceRoomIds(spaceId, accountId, conversationId)
        source
            .binding(spaceId, accountId, conversationId)
            ?.toSpaceRoomBinding(spaceId, accountId, conversationId)
    }

    override fun status(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): Result<CollaborationSpaceRoomStatus?> = safeDatabaseCall {
        requireSpaceRoomIds(spaceId, accountId, conversationId)
        val rows = source.status(spaceId, accountId, conversationId)
        require(rows.size <= 1)
        rows.singleOrNull()?.toSpaceRoomStatus(spaceId, conversationId)
    }

    override fun provisioning(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
        lock: Boolean,
    ): Result<CollaborationSpaceRoomProvisioning?> = safeDatabaseCall {
        requireSpaceRoomIds(spaceId, accountId, conversationId)
        source
            .find(spaceId, accountId, conversationId, lock)
            ?.let(::spaceRoomProvisioningFromRow)
            ?.also {
                require(
                    it.request.spaceId == spaceId &&
                        it.request.accountId == accountId &&
                        it.request.conversationId == conversationId
                )
            }
    }

    override fun enqueue(request: CollaborationSpaceRoomRequest): Result<Unit> = safeDatabaseCall {
        require(request.revision == 0L)
        source.insert(spaceRoomRequestRow(request))
        val stored =
            requireNotNull(
                    source.find(request.spaceId, request.accountId, request.conversationId, false)
                )
                .let(::spaceRoomProvisioningFromRow)
        require(
            stored.request == request &&
                stored.createdAt == request.requestedAt &&
                stored.availableAt == request.requestedAt
        )
        require(
            stored.state == CollaborationSpaceProvisioningState.QUEUED &&
                stored.attempts == 0 &&
                stored.lease == null &&
                stored.lastOutcome == null
        )
    }

    override fun retry(
        request: CollaborationSpaceRoomRequest,
        expectedRevision: Long,
    ): Result<Unit> =
        safeDatabaseCall {
                require(expectedRevision in 0..999998 && request.revision == expectedRevision + 1)
                val row = spaceRoomRequestRow(request)
                val prior =
                    source
                        .find(request.spaceId, request.accountId, request.conversationId, false)
                        ?.let(::spaceRoomProvisioningFromRow) ?: return@safeDatabaseCall false
                require(
                    prior.request.spaceId == request.spaceId &&
                        prior.request.accountId == request.accountId &&
                        prior.request.conversationId == request.conversationId
                )
                if (
                    prior.request.revision != expectedRevision ||
                        prior.state !in
                            setOf(
                                CollaborationSpaceProvisioningState.SUCCEEDED,
                                CollaborationSpaceProvisioningState.FAILED,
                                CollaborationSpaceProvisioningState.CANCELLED,
                            )
                )
                    return@safeDatabaseCall false
                if (!source.retry(row, expectedRevision)) return@safeDatabaseCall false
                val stored =
                    requireNotNull(
                            source.find(
                                request.spaceId,
                                request.accountId,
                                request.conversationId,
                                false,
                            )
                        )
                        .let(::spaceRoomProvisioningFromRow)
                require(
                    stored.request == request &&
                        stored.createdAt == prior.createdAt &&
                        stored.availableAt == request.requestedAt
                )
                require(
                    stored.state == CollaborationSpaceProvisioningState.QUEUED &&
                        stored.attempts == 0 &&
                        stored.lease == null &&
                        stored.lastOutcome == prior.lastOutcome
                )
                true
            }
            .flatMap { if (it) Result.Success(Unit) else changed() }

    override fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
    ): Result<List<CollaborationSpaceRoomProvisioning>> = safeDatabaseCall {
        requireSpaceRoomIds(ownerId)
        require(limit in 1..8 && leaseSeconds in 1..60)
        val rows = source.claim(ownerId, limit, leaseSeconds)
        require(rows.size <= limit)
        rows.map(::spaceRoomProvisioningFromRow).also { values ->
            require(
                values.all {
                    it.state == CollaborationSpaceProvisioningState.RUNNING &&
                        it.lease?.ownerId == ownerId
                }
            )
            require(
                values.map { it.request.spaceId to it.request.conversationId }.toSet().size ==
                    values.size
            )
            require(values.map { it.lease?.token }.toSet().size == values.size)
        }
    }

    override fun finish(
        lease: CollaborationSpaceRoomLease,
        failure: CollaborationSpaceRoomFailure?,
    ): Result<Boolean> = safeDatabaseCall { source.finish(raw(lease), failure?.name) }

    override fun defer(lease: CollaborationSpaceRoomLease, delaySeconds: Int): Result<Boolean> =
        safeDatabaseCall {
            require(delaySeconds in 1..300 && lease.attempt < 8)
            source.defer(raw(lease), delaySeconds)
        }

    override fun cancel(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
        expectedRevision: Long,
    ): Result<Boolean> = safeDatabaseCall {
        requireSpaceRoomIds(spaceId, accountId, conversationId)
        require(expectedRevision in 0..999999)
        source.cancel(spaceId, accountId, conversationId, expectedRevision)
    }

    override fun publish(value: CollaborationSpaceRoomBinding): Result<Unit> = safeDatabaseCall {
        source.insertBinding(spaceRoomBindingRow(value))
        require(
            source
                .binding(value.spaceId, value.publishedBy, value.conversationId)
                ?.toSpaceRoomBinding(value.spaceId, value.publishedBy, value.conversationId) ==
                value
        )
    }

    private fun raw(lease: CollaborationSpaceRoomLease): CollaborationSpaceRoomLeaseData {
        val q = lease.request
        validateSpaceRoomRequest(q)
        requireSpaceRoomIds(lease.ownerId, lease.token)
        requireSpaceRoomTime(lease.until)
        require(lease.attempt in 1..8 && lease.until > q.requestedAt && lease.until <= q.expiresAt)
        return CollaborationSpaceRoomLeaseData(
            q.spaceId,
            q.conversationId,
            q.accountId,
            q.deviceId,
            q.deviceVersion,
            q.providerGeneration,
            q.nativeSessionId,
            q.revision,
            q.nativeSessionVersion,
            q.credentialVersion,
            q.authenticatedAt.atOffset(UTC),
            q.mfaVerifiedAt?.atOffset(UTC),
            q.correlationId,
            q.client.platform.name,
            q.client.build.toLong(),
            q.requestedAt.atOffset(UTC),
            q.expiresAt.atOffset(UTC),
            lease.attempt,
            lease.ownerId,
            lease.token,
            lease.until.atOffset(UTC),
        )
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed"))
}
