package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationLegacyRuntimeRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeRecord
import java.time.Instant
import java.time.ZoneOffset.UTC

class StoredCollaborationLegacyRuntimeRepository(
    private val source: CollaborationLegacyRuntimeDataSource
) : CollaborationLegacyRuntimeRepository {
    override fun ensure(
        owner: CollaborationLegacyRuntimeOwner,
        at: Instant,
    ): Result<CollaborationLegacyRuntime> = safeDatabaseCall {
        val row =
            owner.row().apply {
                phase = "LEGACY"
                version = 0
                createdAt = at.atOffset(UTC)
            }
        legacyRuntimeFromRow(row, owner)
        source.insertIfAbsent(row)
        legacyRuntimeFromRow(requireNotNull(source.find(owner.raw(), lock = true)), owner)
    }

    override fun find(
        owner: CollaborationLegacyRuntimeOwner,
        lock: Boolean,
    ): Result<CollaborationLegacyRuntime?> = safeDatabaseCall {
        source.find(owner.raw(), lock)?.let { legacyRuntimeFromRow(it, owner) }
    }

    override fun lease(
        owner: CollaborationLegacyRuntimeOwner,
        slot: Int,
        lock: Boolean,
    ): Result<CollaborationLegacyRuntimeLease?> = safeDatabaseCall {
        require(slot in 0..7)
        source
            .lease(owner.raw(), slot, lock)
            ?.let { legacyRuntimeLeaseFromRow(it, owner) }
            ?.also { require(it.slot == slot) }
    }

    override fun slots(
        owner: CollaborationLegacyRuntimeOwner
    ): Result<List<CollaborationLegacyRuntimeLease>> = safeDatabaseCall {
        val rows = source.slots(owner.raw())
        require(rows.size <= 8)
        rows
            .map { legacyRuntimeLeaseFromRow(it, owner) }
            .also { leases ->
                require(leases.map { it.slot }.toSet().size == leases.size)
                require(leases.map { it.token }.toSet().size == leases.size)
            }
    }

    override fun claim(
        owner: CollaborationLegacyRuntimeOwner,
        until: Instant,
    ): Result<CollaborationLegacyRuntimeLease?> = safeDatabaseCall {
        require(until.nano % 1000 == 0)
        val rows = source.claim(owner.raw(), until.atOffset(UTC))
        require(rows.size <= 1)
        rows
            .singleOrNull()
            ?.let { legacyRuntimeLeaseFromRow(it, owner) }
            ?.also { require(it.releasedAt == null && it.expiresAt == until) }
    }

    override fun release(lease: CollaborationLegacyRuntimeLease): Result<Boolean> =
        safeDatabaseCall {
            require(lease.releasedAt == null)
            source.release(legacyRuntimeLeaseRow(lease))
        }

    override fun freeze(
        owner: CollaborationLegacyRuntimeOwner,
        target: CollaborationLegacyRuntimeTarget,
        expectedVersion: Long,
        at: Instant,
    ): Result<Boolean> = safeDatabaseCall {
        require(expectedVersion == 0L && at.nano % 1000 == 0)
        source.freeze(
            owner.row().apply {
                frozenAt = at.atOffset(UTC)
                targetSpaceId = target.spaceId
                targetDeviceId = target.deviceId
            },
            expectedVersion,
        )
    }

    private fun CollaborationLegacyRuntimeOwner.raw() =
        CollaborationLegacyRuntimeCoordinatesData(companyId, deviceId, accountId, nativeSessionId)

    private fun CollaborationLegacyRuntimeOwner.row() =
        CollaborationLegacyRuntimeRecord().also {
            it.companyId = companyId
            it.deviceId = deviceId
            it.accountId = accountId
            it.nativeSessionId = nativeSessionId
        }
}
