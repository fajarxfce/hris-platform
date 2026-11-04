package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeLeasesRecord
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC

fun legacyRuntimeFromRow(
    row: CollaborationLegacyRuntimeRecord,
    owner: CollaborationLegacyRuntimeOwner,
): CollaborationLegacyRuntime {
    require(
        row.companyId == owner.companyId &&
            row.deviceId == owner.deviceId &&
            row.accountId == owner.accountId &&
            row.nativeSessionId == owner.nativeSessionId
    )
    val phase = CollaborationLegacyRuntimePhase.valueOf(requireNotNull(row.phase))
    val version = requireNotNull(row.version)
    val created = time(row.createdAt)
    val frozen = row.frozenAt?.let(::time)
    val target =
        if (phase == CollaborationLegacyRuntimePhase.LEGACY) {
            require(
                version == 0L &&
                    frozen == null &&
                    row.targetSpaceId == null &&
                    row.targetDeviceId == null
            )
            null
        } else {
            require(version == 1L && frozen != null && frozen >= created)
            CollaborationLegacyRuntimeTarget(
                requireNotNull(row.targetSpaceId),
                requireNotNull(row.targetDeviceId),
            )
        }
    return CollaborationLegacyRuntime(owner, phase, version, created, frozen, target)
}

fun legacyRuntimeLeaseFromRow(
    row: CollaborationLegacyRuntimeLeasesRecord,
    owner: CollaborationLegacyRuntimeOwner,
): CollaborationLegacyRuntimeLease {
    require(
        row.companyId == owner.companyId &&
            row.deviceId == owner.deviceId &&
            row.accountId == owner.accountId &&
            row.nativeSessionId == owner.nativeSessionId
    )
    val slot = requireNotNull(row.slot)
    require(slot in 0..7)
    val version = requireNotNull(row.runtimeVersion)
    require(version == 0L)
    val started = time(row.startedAt)
    val expires = time(row.expiresAt)
    val released = row.releasedAt?.let(::time)
    require(expires > started && expires <= started.plusSeconds(60))
    require(released == null || released >= started)
    return CollaborationLegacyRuntimeLease(
        owner,
        slot,
        version,
        requireNotNull(row.token),
        started,
        expires,
        released,
    )
}

fun legacyRuntimeLeaseRow(
    lease: CollaborationLegacyRuntimeLease
): CollaborationLegacyRuntimeLeasesRecord =
    CollaborationLegacyRuntimeLeasesRecord()
        .apply {
            companyId = lease.owner.companyId
            deviceId = lease.owner.deviceId
            accountId = lease.owner.accountId
            nativeSessionId = lease.owner.nativeSessionId
            slot = lease.slot
            runtimeVersion = lease.runtimeVersion
            token = lease.token
            startedAt = lease.startedAt.atOffset(UTC)
            expiresAt = lease.expiresAt.atOffset(UTC)
            releasedAt = lease.releasedAt?.atOffset(UTC)
        }
        .also { require(legacyRuntimeLeaseFromRow(it, lease.owner) == lease) }

private fun time(value: OffsetDateTime?): Instant {
    val at = requireNotNull(value).toInstant()
    require(at.nano % 1000 == 0 && at.epochSecond in -62135596800L..253402300799L)
    return at
}
