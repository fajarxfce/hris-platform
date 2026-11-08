package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationReadinessWakeRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationReadinessWakesRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

class StoredCollaborationReadinessWakeRepository(
    private val source: CollaborationReadinessWakeDataSource
) : CollaborationReadinessWakeRepository {
    override fun claim(ownerId: UUID, limit: Int): Result<List<CollaborationReadinessLease>> =
        safeDatabaseCall {
            require(limit in 1..4)
            val rows = source.claim(ownerId, limit)
            require(rows.size <= limit)
            rows
                .map { checkpoint(it).lease }
                .also { require(it.all { lease -> lease.ownerId == ownerId }) }
        }

    override fun find(
        lease: CollaborationReadinessLease,
        lock: Boolean,
    ): Result<CollaborationReadinessCheckpoint?> = safeDatabaseCall {
        source.find(raw(lease), lock)?.let(::checkpoint)
    }

    override fun targets(
        checkpoint: CollaborationReadinessCheckpoint,
        limit: Int,
    ): Result<List<CollaborationReadinessTarget>> = safeDatabaseCall {
        require(limit in 1..8)
        val rows = source.targets(raw(checkpoint), limit)
        require(rows.size <= limit)
        rows.map { row ->
            CollaborationReadinessTarget(
                CollaborationReadinessPosition(row.targetAccountId, row.targetDeviceId),
                row.nativeSessionId,
            )
        }
    }

    override fun advance(
        checkpoint: CollaborationReadinessCheckpoint,
        next: CollaborationReadinessPosition,
    ): Result<Boolean> = safeDatabaseCall {
        source.advance(raw(checkpoint), next.accountId, next.deviceId)
    }

    override fun release(
        checkpoint: CollaborationReadinessCheckpoint,
        complete: Boolean,
        availableAt: Instant,
    ): Result<Boolean> = safeDatabaseCall {
        source.release(raw(checkpoint), complete, availableAt.atOffset(UTC))
    }

    private fun raw(lease: CollaborationReadinessLease) =
        CollaborationReadinessLeaseData(
            lease.eventId,
            lease.companyId,
            lease.publisherAccountId,
            lease.publisherDeviceId,
            lease.ownerId,
            lease.token,
            lease.expiresAt.atOffset(UTC),
        )

    private fun raw(checkpoint: CollaborationReadinessCheckpoint) =
        CollaborationReadinessCheckpointData(
            raw(checkpoint.lease),
            checkpoint.publicationCutoff.atOffset(UTC),
            checkpoint.after?.accountId,
            checkpoint.after?.deviceId,
        )

    private fun checkpoint(
        row: CollaborationReadinessWakesRecord
    ): CollaborationReadinessCheckpoint {
        require((row.afterAccountId == null) == (row.afterDeviceId == null))
        val after =
            row.afterAccountId?.let {
                CollaborationReadinessPosition(it, requireNotNull(row.afterDeviceId))
            }
        return CollaborationReadinessCheckpoint(
            CollaborationReadinessLease(
                row.eventId,
                row.companyId,
                row.publisherAccountId,
                row.publisherDeviceId,
                requireNotNull(row.leaseOwner),
                requireNotNull(row.leaseToken),
                requireNotNull(row.leaseUntil).toInstant(),
            ),
            row.publicationCutoff.toInstant(),
            after,
        )
    }
}
