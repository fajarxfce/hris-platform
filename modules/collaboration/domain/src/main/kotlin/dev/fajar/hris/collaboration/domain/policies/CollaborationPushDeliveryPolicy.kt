package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.push.domain.entities.*
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A later callback may replace the visible identity while an older revision is leased. */
fun collaborationPushVisibleEvent(
    head: CollaborationPushHead,
    lease: CollaborationPushLease,
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

/** Fixed opaque wake envelope. Every company partition still requires current client admission. */
fun collaborationPushMessage(
    head: CollaborationPushHead,
    lease: CollaborationPushLease,
    target: CollaborationPushTarget.Ready,
    token: String,
    now: Instant,
    deadline: Instant,
): Result<PushMessage> {
    val binding = target.binding
    if (
        head.lease != lease ||
            head.companyId != binding.companyId ||
            head.accountId != binding.accountId ||
            head.deviceId != binding.deviceId ||
            head.nativeSessionId != binding.nativeSessionId ||
            lease.companyId != binding.companyId ||
            lease.accountId != binding.accountId ||
            lease.deviceId != binding.deviceId ||
            lease.nativeSessionId != binding.nativeSessionId ||
            lease.revision <= head.deliveredRevision ||
            lease.revision > head.revision ||
            target.observedAt > now
    )
        return collaborationPushLeaseLost()
    val expiry = minOf(head.expiresAt, now.plusSeconds(86400))
    val dispatch = minOf(deadline, lease.expiresAt, target.expiresAt, expiry)
    if (dispatch <= now || Duration.between(now, expiry).seconds < 1)
        return collaborationPushLeaseLost()
    val event = collaborationPushVisibleEvent(head, lease, now)
    val identity =
        UUID.nameUUIDFromBytes(
            "hris-chat-wake-v1:${binding.deviceId}:${lease.revision}".toByteArray(Charsets.UTF_8)
        )
    val data = buildMap {
        put("schemaVersion", "1")
        put("eventCode", "collaboration.chat_available")
        put("eventId", identity.toString())
        put("accountId", binding.accountId.toString())
        put("companyId", binding.companyId.toString())
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

/** Bounded retries belong to this durable revision, never the provider transport. */
fun collaborationPushRetryAt(
    failure: Failure,
    attempts: Int,
    now: Instant,
    expiresAt: Instant,
): Instant? {
    if (
        attempts !in 1..7 ||
            failure.kind !in setOf(FailureKind.UNAVAILABLE, FailureKind.RATE_LIMITED)
    )
        return null
    val seconds =
        maxOf(5L shl (attempts - 1), failure.parameters["retryAfterSeconds"]?.toLongOrNull() ?: 0)
            .coerceIn(5, 300)
    return now.plusSeconds(seconds).takeIf { it < expiresAt }
}

fun collaborationPushLeaseLost() =
    Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_push_lease_lost"))
