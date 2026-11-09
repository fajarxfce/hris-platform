package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import java.time.Instant
import java.util.UUID

fun validCollaborationSpaceReadinessTime(value: Instant): Boolean =
    value > Instant.EPOCH && value.epochSecond <= 253402300799L && value.nano % 1000 == 0

fun validCollaborationSpaceReadinessLease(value: CollaborationSpaceReadinessLease): Boolean =
    listOf(
            value.eventId,
            value.spaceId,
            value.publisherAccountId,
            value.publisherDeviceId,
            value.ownerId,
            value.token,
        )
        .none { it == UUID(0, 0) } && validCollaborationSpaceReadinessTime(value.until)

fun validCollaborationSpaceReadinessCheckpoint(
    value: CollaborationSpaceReadinessCheckpoint
): Boolean =
    validCollaborationSpaceReadinessLease(value.lease) &&
        value.publicationRevision > 0 &&
        listOf(value.publicationCutoff, value.capturedAt, value.expiresAt, value.availableAt)
            .all(::validCollaborationSpaceReadinessTime) &&
        value.expiresAt == value.publicationCutoff.plusSeconds(86400) &&
        value.publicationCutoff <= value.capturedAt &&
        value.capturedAt <= value.availableAt &&
        value.availableAt < value.lease.until &&
        value.lease.until <= value.expiresAt &&
        value.attempts in 0..8 &&
        (value.after == null ||
            (value.after.accountId != UUID(0, 0) &&
                value.after.deviceId != UUID(0, 0) &&
                value.after.accountId != value.lease.publisherAccountId))
