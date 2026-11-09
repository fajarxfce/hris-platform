package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceReadinessQueueDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpaceReadinessTime
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceReadinessQueueRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

class StoredCollaborationSpaceReadinessQueueRepository(
    private val source: CollaborationSpaceReadinessQueueDataSource
) : CollaborationSpaceReadinessQueueRepository {
    override fun claim(
        channel: CollaborationSpaceReadinessChannel,
        owner: UUID,
        limit: Int,
    ): Result<List<CollaborationSpaceReadinessLease>> = safeDatabaseCall {
        require(owner != UUID(0, 0) && limit in 1..4)
        val rows = source.claim(channel.name, owner, limit)
        require(rows.size <= limit)
        val checkpoints = rows.map { it.toSpaceReadinessCheckpoint() }
        require(
            checkpoints.all {
                it.lease.channel == channel && it.lease.ownerId == owner && it.attempts in 1..8
            }
        )
        val leases = checkpoints.map { it.lease }
        require(
            leases.map { it.eventId }.distinct().size == leases.size &&
                leases.map { it.token }.distinct().size == leases.size
        )
        leases
    }

    override fun find(
        lease: CollaborationSpaceReadinessLease,
        lock: Boolean,
    ): Result<CollaborationSpaceReadinessCheckpoint?> = safeDatabaseCall {
        source.find(lease.toSpaceReadinessData(), lock)?.toSpaceReadinessCheckpoint()?.also {
            require(it.lease == lease)
        }
    }

    override fun targets(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        limit: Int,
    ): Result<List<CollaborationReadinessTarget>> = safeDatabaseCall {
        require(limit in 1..8)
        val rows = source.targets(checkpoint.toSpaceReadinessData(), limit)
        require(rows.size <= limit)
        val targets = rows.map { it.toSpaceReadinessTarget(checkpoint) }
        require(targets.zipWithNext().all { (first, second) -> first.position < second.position })
        require(targets.map { it.nativeSessionId }.distinct().size == targets.size)
        targets
    }

    override fun advance(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        next: CollaborationReadinessPosition,
    ): Result<Boolean> = safeDatabaseCall {
        val raw = checkpoint.toSpaceReadinessData()
        require(
            next.accountId != UUID(0, 0) &&
                next.deviceId != UUID(0, 0) &&
                next.accountId != checkpoint.lease.publisherAccountId
        )
        val after = checkpoint.after
        require(after == null || next > after)
        val changed = source.advance(raw, next.accountId, next.deviceId)
        if (changed)
            require(
                source.find(raw.lease, lock = true)?.toSpaceReadinessCheckpoint() ==
                    checkpoint.copy(after = next, attempts = 0)
            )
        changed
    }

    override fun release(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        complete: Boolean,
        availableAt: Instant,
    ): Result<Boolean> = safeDatabaseCall {
        val raw = checkpoint.toSpaceReadinessData()
        require(
            validCollaborationSpaceReadinessTime(availableAt) &&
                availableAt >= checkpoint.capturedAt
        )
        val changed = source.release(raw, complete, availableAt.atOffset(UTC))
        if (changed)
            require(
                source
                    .read(raw.lease.eventId, raw.lease.channel)
                    ?.matchesSpaceReadinessRelease(checkpoint, complete, availableAt) == true
            )
        changed
    }
}
