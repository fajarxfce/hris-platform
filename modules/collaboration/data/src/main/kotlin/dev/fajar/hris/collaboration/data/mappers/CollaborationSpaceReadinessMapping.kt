package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceReadinessDeliveriesRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceReadinessTargetsRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

fun CollaborationSpaceReadinessLease.toSpaceReadinessData(): CollaborationSpaceReadinessLeaseData {
    require(validCollaborationSpaceReadinessLease(this))
    return CollaborationSpaceReadinessLeaseData(
        eventId,
        channel.name,
        spaceId,
        publisherAccountId,
        publisherDeviceId,
        ownerId,
        token,
        until.atOffset(UTC),
    )
}

fun CollaborationSpaceReadinessCheckpoint.toSpaceReadinessData():
    CollaborationSpaceReadinessCheckpointData {
    require(validCollaborationSpaceReadinessCheckpoint(this))
    return CollaborationSpaceReadinessCheckpointData(
        lease.toSpaceReadinessData(),
        publicationRevision,
        publicationCutoff.atOffset(UTC),
        capturedAt.atOffset(UTC),
        expiresAt.atOffset(UTC),
        availableAt.atOffset(UTC),
        attempts,
        after?.accountId,
        after?.deviceId,
    )
}

fun CollaborationSpaceReadinessDeliveriesRecord.toSpaceReadinessCheckpoint():
    CollaborationSpaceReadinessCheckpoint {
    require(completedAt == null)
    require((afterAccountId == null) == (afterDeviceId == null))
    val lease =
        CollaborationSpaceReadinessLease(
            requireNotNull(eventId),
            CollaborationSpaceReadinessChannel.valueOf(requireNotNull(channel)),
            requireNotNull(spaceId),
            requireNotNull(publisherAccountId),
            requireNotNull(publisherDeviceId),
            requireNotNull(leaseOwner),
            requireNotNull(leaseToken),
            requireNotNull(leaseUntil).toInstant(),
        )
    return CollaborationSpaceReadinessCheckpoint(
            lease,
            requireNotNull(publicationRevision),
            requireNotNull(publicationCutoff).toInstant(),
            requireNotNull(capturedAt).toInstant(),
            requireNotNull(expiresAt).toInstant(),
            requireNotNull(availableAt).toInstant(),
            requireNotNull(attempts),
            afterAccountId?.let {
                CollaborationReadinessPosition(it, requireNotNull(afterDeviceId))
            },
        )
        .also { require(validCollaborationSpaceReadinessCheckpoint(it)) }
}

fun CollaborationSpaceReadinessTargetsRecord.toSpaceReadinessTarget(
    expected: CollaborationSpaceReadinessCheckpoint
): CollaborationReadinessTarget {
    require(
        eventId == expected.lease.eventId &&
            channel == expected.lease.channel.name &&
            spaceId == expected.lease.spaceId &&
            publisherAccountId == expected.lease.publisherAccountId &&
            publisherDeviceId == expected.lease.publisherDeviceId &&
            leaseOwner == expected.lease.ownerId &&
            leaseToken == expected.lease.token &&
            leaseUntil?.toInstant() == expected.lease.until &&
            publicationRevision == expected.publicationRevision &&
            publicationCutoff?.toInstant() == expected.publicationCutoff &&
            capturedAt?.toInstant() == expected.capturedAt &&
            expiresAt?.toInstant() == expected.expiresAt &&
            availableAt?.toInstant() == expected.availableAt &&
            attempts == expected.attempts &&
            afterAccountId == expected.after?.accountId &&
            afterDeviceId == expected.after?.deviceId
    )
    val position =
        CollaborationReadinessPosition(
            requireNotNull(targetAccountId),
            requireNotNull(targetDeviceId),
        )
    val parent = requireNotNull(nativeSessionId)
    require(
        position.accountId != UUID(0, 0) && position.deviceId != UUID(0, 0) && parent != UUID(0, 0)
    )
    val after = expected.after
    require(
        position.accountId != expected.lease.publisherAccountId &&
            (after == null || position > after)
    )
    return CollaborationReadinessTarget(position, parent)
}

/** Validate the full released row without pretending its old token still owns an active lease. */
fun CollaborationSpaceReadinessDeliveriesRecord.matchesSpaceReadinessRelease(
    expected: CollaborationSpaceReadinessCheckpoint,
    complete: Boolean,
    available: Instant,
): Boolean {
    if (
        eventId != expected.lease.eventId ||
            channel != expected.lease.channel.name ||
            spaceId != expected.lease.spaceId ||
            publisherAccountId != expected.lease.publisherAccountId ||
            publisherDeviceId != expected.lease.publisherDeviceId ||
            publicationRevision != expected.publicationRevision ||
            publicationCutoff?.toInstant() != expected.publicationCutoff ||
            capturedAt?.toInstant() != expected.capturedAt ||
            expiresAt?.toInstant() != expected.expiresAt ||
            afterAccountId != expected.after?.accountId ||
            afterDeviceId != expected.after?.deviceId ||
            attempts != expected.attempts ||
            leaseOwner != null ||
            leaseToken != null ||
            leaseUntil != null ||
            availableAt?.toInstant() != available
    )
        return false
    val completed = completedAt?.toInstant()
    return if (!complete) completed == null
    else
        completed != null &&
            validCollaborationSpaceReadinessTime(completed) &&
            completed >= expected.capturedAt &&
            completed < expected.lease.until
}
