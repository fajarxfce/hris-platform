package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.push.domain.entities.*
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A later callback may replace the visible identity while an older revision is leased. */
fun collaborationSpacePushVisibleEvent(
    head: CollaborationSpacePushHead,
    lease: CollaborationSpacePushLease,
    now: Instant,
): CollaborationPushEvent? {
    val at = head.visibleAt ?: return null
    return head.event?.takeIf {
        validCollaborationPushEvent(it) &&
            head.visibleRevision > head.deliveredRevision &&
            head.visibleRevision <= lease.revision &&
            at <= now &&
            at > now.minusSeconds(86400)
    }
}

/**
 * Fixed opaque wake envelope. Every organization partition still requires current client admission.
 */
fun collaborationSpacePushMessage(
    head: CollaborationSpacePushHead,
    lease: CollaborationSpacePushLease,
    target: CollaborationSpacePushTarget.Ready,
    token: String,
    now: Instant,
    deadline: Instant,
): Result<PushMessage> {
    val binding = target.binding
    if (
        !validCollaborationSpacePushHead(head) ||
            !validCollaborationSpacePushLease(lease) ||
            head.lease != lease ||
            head.spaceId != binding.spaceId ||
            head.accountId != binding.accountId ||
            head.deviceId != binding.deviceId ||
            head.nativeSessionId != binding.nativeSessionId ||
            lease.spaceId != binding.spaceId ||
            lease.accountId != binding.accountId ||
            lease.deviceId != binding.deviceId ||
            lease.nativeSessionId != binding.nativeSessionId ||
            lease.revision <= head.deliveredRevision ||
            lease.revision > head.revision ||
            target.observedAt > now ||
            token.length !in 1..4096 ||
            token.any { it.code !in 33..126 }
    )
        return collaborationPushLeaseLost()
    val expiry = minOf(head.expiresAt, now.plusSeconds(86400))
    val dispatch = minOf(deadline, lease.expiresAt, target.expiresAt, expiry)
    if (dispatch <= now || Duration.between(now, expiry).seconds < 1)
        return collaborationPushLeaseLost()
    val event = collaborationSpacePushVisibleEvent(head, lease, now)
    val identity =
        UUID.nameUUIDFromBytes(
            "hris-group-chat-wake-v1:${binding.deviceId}:${lease.revision}"
                .toByteArray(Charsets.UTF_8)
        )
    val data = buildMap {
        put("schemaVersion", "1")
        put("eventCode", "collaboration.space_chat_available")
        put("eventId", identity.toString())
        put("accountId", binding.accountId.toString())
        put("spaceId", binding.spaceId.toString())
        put("nativeSessionId", binding.nativeSessionId.toString())
        put("deviceId", binding.deviceId.toString())
        put("revision", lease.revision.toString())
        put("issuedAt", now.epochSecond.toString())
        put("expiresAt", expiry.epochSecond.toString())
        if (event != null) {
            put("matrixRoomId", event.roomId)
            put("matrixEventId", event.eventId)
        }
    }
    return Result.Success(
        PushMessage(
            token,
            data,
            expiry,
            if (event == null) PushDelivery.Background("hris_chat")
            else PushDelivery.Visible("HRIS", "New chat activity", "hris_chat"),
            dispatchDeadline = dispatch,
            retention = PushRetention.RECOVERABLE,
        )
    )
}
