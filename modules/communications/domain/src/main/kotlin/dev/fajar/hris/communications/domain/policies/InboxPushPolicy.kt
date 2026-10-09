package dev.fajar.hris.communications.domain.policies

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.nativeSessionActive
import java.time.Instant

fun inboxPushAccess(access: AccountAccess?): Boolean =
    access != null &&
        access.account.active &&
        access.companyActive &&
        access.membershipActive &&
        "announcements.read" in access.permissions

fun inboxPushTargetActive(
    registration: NativePushRegistration,
    session: NativeSession?,
    account: Account,
    publishedAt: Instant,
    now: Instant,
): Boolean =
    registration.accountId == account.id &&
        registration.enabled &&
        registration.expiresAt.isAfter(now) &&
        !registration.registeredAt.isAfter(publishedAt) &&
        session != null &&
        session.id == registration.sessionId &&
        session.accountId == account.id &&
        session.credentialVersion == account.securityVersion &&
        nativeSessionActive(session, now)

fun inboxPushRetryAt(
    failure: Failure,
    attempts: Int,
    now: Instant,
    expiresAt: Instant,
    maximumAttempts: Int,
): Instant? {
    if (
        attempts !in 1 until maximumAttempts ||
            failure.kind !in setOf(FailureKind.UNAVAILABLE, FailureKind.RATE_LIMITED)
    )
        return null
    val backoff = minOf(300L, 5L shl (attempts - 1).coerceAtMost(6))
    val providerDelay =
        failure.parameters["retryAfterSeconds"]?.toLongOrNull()?.coerceIn(0, 86400) ?: 0
    val next = now.plusSeconds(maxOf(backoff, providerDelay))
    return next.takeIf { it.isBefore(expiresAt) }
}

fun Result<Boolean>.requirePushLease(): Result<Unit> = flatMap {
    if (it) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.CONFLICT, "push_lease_lost"))
}

fun inboxPushFinishedRecord(dispatch: InboxPushDispatch, state: InboxPushState, code: String?) =
    ChangeRecord(
        "inbox_item",
        dispatch.inboxId,
        "communications.push_${state.name.lowercase()}",
        mapOf(
            "processed" to dispatch.processedCount.toString(),
            "accepted" to dispatch.acceptedCount.toString(),
            "rejected" to dispatch.rejectedCount.toString(),
            "code" to (code ?: "complete"),
        ),
    )
