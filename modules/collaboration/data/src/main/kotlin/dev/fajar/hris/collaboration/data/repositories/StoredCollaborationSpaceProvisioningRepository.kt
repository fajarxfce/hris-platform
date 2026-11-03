package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceProvisioningDataSource
import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceProvisioningLeaseData
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceProvisioningRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.time.ZoneOffset.UTC
import java.util.UUID

class StoredCollaborationSpaceProvisioningRepository(
    private val source: CollaborationSpaceProvisioningDataSource
) : CollaborationSpaceProvisioningRepository {
    override fun metadata(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): Result<CollaborationSpaceProvisioning?> = safeDatabaseCall {
        source.find(spaceId, accountId, deviceId, lock)?.let(::spaceProvisioningFromRow)?.also {
            require(
                it.request.spaceId == spaceId &&
                    it.request.accountId == accountId &&
                    it.request.deviceId == deviceId
            )
        }
    }

    override fun enqueue(request: CollaborationSpaceProvisioningRequest): Result<Unit> =
        safeDatabaseCall {
            require(request.revision == 0L)
            source.insert(spaceProvisioningRequestRow(request))
        }

    override fun retry(
        request: CollaborationSpaceProvisioningRequest,
        expectedRevision: Long,
    ): Result<Unit> =
        safeDatabaseCall {
                require(expectedRevision in 0..999998 && request.revision == expectedRevision + 1)
                source.retry(spaceProvisioningRequestRow(request), expectedRevision)
            }
            .flatMap {
                if (it) Result.Success(Unit)
                else
                    Result.Failed(
                        Failure(FailureKind.CONFLICT, "collaboration_provisioning_changed")
                    )
            }

    override fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
    ): Result<List<CollaborationSpaceProvisioning>> = safeDatabaseCall {
        require(limit in 1..8 && leaseSeconds in 1..60)
        val rows = source.claim(ownerId, limit, leaseSeconds)
        require(rows.size <= limit)
        rows.map(::spaceProvisioningFromRow).also { heads ->
            require(
                heads.all {
                    it.state == CollaborationSpaceProvisioningState.RUNNING &&
                        it.lease?.ownerId == ownerId
                }
            )
            require(
                heads.map { it.request.spaceId to it.request.deviceId }.toSet().size == heads.size
            )
            require(heads.map { it.lease?.token }.toSet().size == heads.size)
        }
    }

    override fun finish(
        lease: CollaborationSpaceProvisioningLease,
        failure: CollaborationSpaceProvisioningFailure?,
    ): Result<Boolean> = safeDatabaseCall { source.finish(raw(lease), failure?.name) }

    override fun defer(
        lease: CollaborationSpaceProvisioningLease,
        delaySeconds: Int,
    ): Result<Boolean> = safeDatabaseCall {
        require(delaySeconds in 1..300 && lease.attempt < 8)
        source.defer(raw(lease), delaySeconds)
    }

    override fun cancel(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedRevision: Long,
    ): Result<Boolean> = safeDatabaseCall {
        require(expectedRevision in 0..999999)
        source.cancel(spaceId, accountId, deviceId, expectedRevision)
    }

    private fun raw(
        lease: CollaborationSpaceProvisioningLease
    ): CollaborationSpaceProvisioningLeaseData {
        val request = lease.request
        validateSpaceProvisioningRequest(request)
        require(request.requestedAt.nano % 1000 == 0 && request.expiresAt.nano % 1000 == 0)
        require(
            lease.attempt in 1..8 &&
                lease.until > request.requestedAt &&
                lease.until <= request.expiresAt &&
                lease.until.nano % 1000 == 0
        )
        return CollaborationSpaceProvisioningLeaseData(
            request.spaceId,
            request.accountId,
            request.deviceId,
            request.nativeSessionId,
            request.revision,
            request.nativeSessionVersion,
            request.credentialVersion,
            request.authenticatedAt.atOffset(UTC),
            request.mfaVerifiedAt?.atOffset(UTC),
            request.correlationId,
            request.client.platform.name,
            request.client.build.toLong(),
            request.requestedAt.atOffset(UTC),
            request.expiresAt.atOffset(UTC),
            lease.attempt,
            lease.ownerId,
            lease.token,
            lease.until.atOffset(UTC),
        )
    }
}
