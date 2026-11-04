package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceRequestDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRequestRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.ZoneOffset.UTC

class StoredCollaborationSpaceRequestRepository(
    private val source: CollaborationSpaceRequestDataSource
) : CollaborationSpaceRequestRepository {
    override fun claim(
        position: CollaborationSpaceRequestPosition,
        until: Instant,
    ): Result<CollaborationSpaceRequestLease?> = safeDatabaseCall {
        validateSpaceRequestPosition(position)
        require(until.nano % 1000 == 0)
        val owner = position.owner.toSpaceRequestCoordinates()
        val rows =
            source.claim(
                owner,
                position.kind.name,
                position.gatewayId,
                position.gatewayVersion,
                until.atOffset(UTC),
            )
        require(rows.size <= 1)
        rows.singleOrNull()?.let { row ->
            val lease = row.toSpaceRequestLease(position.owner, requireNotNull(row.slot))
            require(lease.position == position && lease.expiresAt == until)
            val stored =
                requireNotNull(source.find(owner, lease.slot, false))
                    .toSpaceRequestLease(position.owner, lease.slot)
            require(stored == lease)
            lease
        }
    }

    override fun find(
        owner: CollaborationSpaceRequestOwner,
        slot: Int,
        lock: Boolean,
    ): Result<CollaborationSpaceRequestLease?> = safeDatabaseCall {
        require(slot in 0..7)
        source.find(owner.toSpaceRequestCoordinates(), slot, lock)?.toSpaceRequestLease(owner, slot)
    }

    override fun release(lease: CollaborationSpaceRequestLease): Result<Boolean> =
        safeDatabaseCall {
            val released = source.release(lease.toSpaceRequestRecord())
            if (released) {
                val remaining =
                    source
                        .find(lease.position.owner.toSpaceRequestCoordinates(), lease.slot, false)
                        ?.toSpaceRequestLease(lease.position.owner, lease.slot)
                require(remaining?.token != lease.token)
            }
            released
        }

    override fun clearExpired(at: Instant, limit: Int): Result<Int> = safeDatabaseCall {
        require(limit in 1..100)
        source.clearExpired(at.atOffset(UTC), limit).also { require(it in 0..limit) }
    }
}
