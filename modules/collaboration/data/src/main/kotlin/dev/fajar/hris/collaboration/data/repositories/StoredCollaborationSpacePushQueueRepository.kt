package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.toCollaborationSpacePushHead
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpacePushQueueRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

/** Caller owns the transaction, current admission and ordered parent/device/queue guards. */
class StoredCollaborationSpacePushQueueRepository(
    private val source: CollaborationSpacePushQueueDataSource
) : CollaborationSpacePushQueueRepository {
    override fun append(
        binding: CollaborationSpacePushBinding,
        event: CollaborationPushEvent?,
        lifetimeSeconds: Int,
    ): Result<Boolean> = safeDatabaseCall {
        require(validCollaborationSpacePushBinding(binding) && lifetimeSeconds in 1..86400)
        require(event == null || validCollaborationPushEvent(event))
        val hash =
            event?.let {
                HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest(
                                ("hris-space-push-event-v1\u0000" +
                                        it.roomId +
                                        "\u0000" +
                                        it.eventId)
                                    .toByteArray(Charsets.UTF_8)
                            )
                    )
            }
        val advanced =
            source.append(
                binding.spaceId,
                binding.deviceId,
                binding.accountId,
                binding.nativeSessionId,
                event?.roomId,
                event?.eventId,
                hash,
                lifetimeSeconds,
            )
        val row =
            requireNotNull(source.find(binding.spaceId, binding.accountId, binding.deviceId, false))
        val stored = row.toCollaborationSpacePushHead()
        requireOwner(
            stored,
            binding.spaceId,
            binding.accountId,
            binding.deviceId,
            binding.nativeSessionId,
        )
        if (hash != null) {
            require(hash in row.recentEventHashes)
            if (advanced)
                require(stored.event == event && stored.visibleRevision == stored.revision)
        } else require(stored.deliveredRevision < stored.revision)
        advanced
    }

    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): Result<CollaborationSpacePushHead?> = safeDatabaseCall {
        requireIds(spaceId, accountId, deviceId)
        source.find(spaceId, accountId, deviceId, lock)?.toCollaborationSpacePushHead()?.also {
            requireOwner(it, spaceId, accountId, deviceId)
        }
    }

    override fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): Result<List<CollaborationSpacePushHead>> = safeDatabaseCall {
        requireIds(ownerId)
        require(limit in 1..8 && leaseSeconds in 1..60 && maximumAttempts in 1..8)
        val rows = source.claim(ownerId, limit, leaseSeconds, maximumAttempts)
        require(rows.size <= limit)
        val values = rows.map { it.toCollaborationSpacePushHead() }
        require(values.map { it.spaceId to it.deviceId }.toSet().size == values.size)
        require(
            values.all { head ->
                val lease = head.lease
                lease != null &&
                    lease.ownerId == ownerId &&
                    lease.revision == head.revision &&
                    head.attempts <= maximumAttempts
            }
        )
        values
    }

    override fun finish(lease: CollaborationSpacePushLease): Result<Boolean> = safeDatabaseCall {
        require(validCollaborationSpacePushLease(lease))
        val finished = source.finish(raw(lease))
        if (finished) {
            val current = after(lease)
            require(current.lease == null && current.deliveredRevision == lease.revision)
        }
        finished
    }

    override fun defer(lease: CollaborationSpacePushLease, availableAt: Instant): Result<Boolean> =
        safeDatabaseCall {
            require(validCollaborationSpacePushLease(lease))
            val at = availableAt.truncatedTo(ChronoUnit.MICROS)
            val deferred = source.defer(raw(lease), at.atOffset(UTC))
            if (deferred) {
                val current = after(lease)
                require(
                    current.lease == null &&
                        current.deliveredRevision < lease.revision &&
                        current.attemptRevision == lease.revision &&
                        current.revision >= lease.revision
                )
                if (current.revision == lease.revision) require(current.availableAt == at)
            }
            deferred
        }

    private fun after(lease: CollaborationSpacePushLease): CollaborationSpacePushHead =
        requireNotNull(source.find(lease.spaceId, lease.accountId, lease.deviceId, false))
            .toCollaborationSpacePushHead()
            .also {
                requireOwner(
                    it,
                    lease.spaceId,
                    lease.accountId,
                    lease.deviceId,
                    lease.nativeSessionId,
                )
            }

    private fun raw(lease: CollaborationSpacePushLease) =
        CollaborationSpacePushLeaseData(
            lease.spaceId,
            lease.accountId,
            lease.deviceId,
            lease.nativeSessionId,
            lease.ownerId,
            lease.token,
            lease.revision,
            lease.expiresAt.atOffset(UTC),
        )

    private fun requireIds(vararg values: UUID) {
        require(values.none { it == UUID(0, 0) })
    }

    private fun requireOwner(
        value: CollaborationSpacePushHead,
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        nativeSessionId: UUID? = null,
    ) {
        require(
            value.spaceId == spaceId &&
                value.accountId == accountId &&
                value.deviceId == deviceId &&
                (nativeSessionId == null || value.nativeSessionId == nativeSessionId)
        )
    }
}
