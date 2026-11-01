package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationPushLeaseData
import dev.fajar.hris.collaboration.data.datasources.CollaborationPushQueueDataSource
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validCollaborationPushEvent
import dev.fajar.hris.collaboration.domain.repositories.CollaborationPushQueueRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationPushHintsRecord
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.HexFormat
import java.util.UUID

class StoredCollaborationPushQueueRepository(private val source: CollaborationPushQueueDataSource) :
    CollaborationPushQueueRepository {
    override fun append(
        binding: CollaborationPushBinding,
        event: CollaborationPushEvent?,
        lifetimeSeconds: Int,
    ): Result<Boolean> = safeDatabaseCall {
        require(lifetimeSeconds in 1..86400)
        require(event == null || validCollaborationPushEvent(event))
        val hash =
            event?.let {
                HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest(
                                ("hris-push-event-v1\u0000" + it.roomId + "\u0000" + it.eventId)
                                    .toByteArray(Charsets.UTF_8)
                            )
                    )
            }
        source.append(
            binding.companyId,
            binding.deviceId,
            binding.accountId,
            binding.nativeSessionId,
            event?.roomId,
            event?.eventId,
            hash,
            lifetimeSeconds,
        )
    }

    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): Result<CollaborationPushHead?> = safeDatabaseCall {
        source.find(companyId, accountId, deviceId, lock)?.let(::head)
    }

    override fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): Result<List<CollaborationPushHead>> = safeDatabaseCall {
        require(limit in 1..8 && leaseSeconds in 1..60 && maximumAttempts in 1..8)
        val rows = source.claim(ownerId, limit, leaseSeconds, maximumAttempts)
        require(rows.size <= limit)
        rows.map(::head).also { require(it.all { h -> h.lease?.ownerId == ownerId }) }
    }

    override fun finish(lease: CollaborationPushLease): Result<Boolean> = safeDatabaseCall {
        source.finish(raw(lease))
    }

    override fun defer(lease: CollaborationPushLease, availableAt: Instant): Result<Boolean> =
        safeDatabaseCall {
            source.defer(raw(lease), availableAt.atOffset(UTC))
        }

    private fun raw(lease: CollaborationPushLease) =
        CollaborationPushLeaseData(
            lease.companyId,
            lease.accountId,
            lease.deviceId,
            lease.nativeSessionId,
            lease.ownerId,
            lease.token,
            lease.revision,
            lease.expiresAt.atOffset(UTC),
        )

    private fun head(row: CollaborationPushHintsRecord): CollaborationPushHead {
        val event = row.eventId?.let { CollaborationPushEvent(requireNotNull(row.eventRoomId), it) }
        require(event == null || validCollaborationPushEvent(event))
        val lease =
            row.leaseToken?.let {
                CollaborationPushLease(
                    row.companyId,
                    row.accountId,
                    row.deviceId,
                    row.nativeSessionId,
                    requireNotNull(row.leaseOwner),
                    it,
                    requireNotNull(row.leaseRevision),
                    requireNotNull(row.leaseUntil).toInstant(),
                )
            }
        return CollaborationPushHead(
            row.companyId,
            row.accountId,
            row.deviceId,
            row.nativeSessionId,
            row.revision,
            row.deliveredRevision,
            row.createdAt.toInstant(),
            row.lastHintAt.toInstant(),
            row.expiresAt.toInstant(),
            row.visibleRevision,
            event,
            row.visibleAt?.toInstant(),
            row.recentEventHashes.size,
            row.availableAt.toInstant(),
            row.attemptRevision,
            row.attempts,
            lease,
        )
    }
}
