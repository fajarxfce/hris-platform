package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpacePushHead
import dev.fajar.hris.schema.tables.records.CollaborationSpacePushHintsRecord

/** Strict storage projection only; current delivery authorization belongs to its use case. */
fun CollaborationSpacePushHintsRecord.toCollaborationSpacePushHead(): CollaborationSpacePushHead {
    val leaseFields = listOf(leaseOwner, leaseToken, leaseUntil, leaseRevision)
    require(leaseFields.all { it == null } || leaseFields.all { it != null })
    require((eventId == null) == (eventRoomId == null))
    val hashes = requireNotNull(recentEventHashes).map { requireNotNull(it) }
    require(
        hashes.size <= 64 &&
            hashes.toSet().size == hashes.size &&
            hashes.all { it.matches(Regex("[a-f0-9]{64}")) }
    )
    val event = eventId?.let { CollaborationPushEvent(requireNotNull(eventRoomId), it) }
    val lease =
        leaseToken?.let {
            CollaborationSpacePushLease(
                spaceId,
                accountId,
                deviceId,
                nativeSessionId,
                requireNotNull(leaseOwner),
                it,
                requireNotNull(leaseRevision),
                requireNotNull(leaseUntil).toInstant(),
            )
        }
    val value =
        CollaborationSpacePushHead(
            spaceId,
            accountId,
            deviceId,
            nativeSessionId,
            revision,
            deliveredRevision,
            requireNotNull(createdAt).toInstant(),
            requireNotNull(lastHintAt).toInstant(),
            requireNotNull(expiresAt).toInstant(),
            visibleRevision,
            event,
            visibleAt?.toInstant(),
            hashes.size,
            requireNotNull(availableAt).toInstant(),
            attemptRevision,
            attempts,
            lease,
        )
    require(validCollaborationSpacePushHead(value))
    return value
}
