package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import java.time.Duration
import java.util.UUID

/** Storage shape only. Current background/content authority is always admitted separately. */
fun validCollaborationSpacePushLease(value: CollaborationSpacePushLease): Boolean =
    listOf(
            value.spaceId,
            value.accountId,
            value.deviceId,
            value.nativeSessionId,
            value.ownerId,
            value.token,
        )
        .none { it == UUID(0, 0) } && value.revision in 1L..999999999999L

fun validCollaborationSpacePushHead(value: CollaborationSpacePushHead): Boolean {
    if (
        listOf(value.spaceId, value.accountId, value.deviceId, value.nativeSessionId).any {
            it == UUID(0, 0)
        } ||
            value.revision !in 1L..999999999999L ||
            value.deliveredRevision !in 0L..value.revision ||
            value.createdAt > value.lastHintAt ||
            value.expiresAt <= value.lastHintAt ||
            Duration.between(value.lastHintAt, value.expiresAt) > Duration.ofDays(1) ||
            value.availableAt < value.createdAt ||
            value.visibleRevision !in 0L..value.revision ||
            value.recentEventCount !in 0..64 ||
            value.attemptRevision !in 0L..value.revision ||
            value.attempts !in 0..8 ||
            ((value.attemptRevision == 0L) != (value.attempts == 0))
    )
        return false
    if (value.visibleRevision == 0L) {
        if (value.event != null || value.visibleAt != null || value.recentEventCount != 0)
            return false
    } else {
        val event = value.event ?: return false
        val visible = value.visibleAt ?: return false
        if (
            !validCollaborationPushEvent(event) ||
                visible < value.createdAt ||
                visible > value.lastHintAt ||
                value.recentEventCount == 0
        )
            return false
    }
    val lease = value.lease ?: return true
    return validCollaborationSpacePushLease(lease) &&
        lease.spaceId == value.spaceId &&
        lease.accountId == value.accountId &&
        lease.deviceId == value.deviceId &&
        lease.nativeSessionId == value.nativeSessionId &&
        lease.revision > value.deliveredRevision &&
        lease.revision <= value.revision &&
        lease.revision == value.attemptRevision &&
        value.attempts > 0 &&
        lease.expiresAt > value.createdAt
    // A later append with a shorter lifetime may legitimately shorten head.expiresAt
    // without replacing an in-flight lease. Delivery must separately bound both times.
}
