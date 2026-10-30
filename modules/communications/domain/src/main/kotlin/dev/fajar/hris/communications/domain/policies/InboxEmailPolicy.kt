package dev.fajar.hris.communications.domain.policies

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.mail.domain.entities.OutboundMail
import java.time.Instant

fun inboxEmailAccess(access: AccountAccess?): Boolean =
    access != null &&
        access.account.active &&
        access.companyActive &&
        access.membershipActive &&
        "announcements.read" in access.permissions

fun inboxEmailMessage(
    dispatch: InboxEmailDispatch,
    address: String,
    policy: InboxEmailPolicy,
): OutboundMail =
    OutboundMail(
        dispatch.inboxId,
        address,
        "New HRIS announcement",
        "An announcement is available in your HRIS Inbox. Sign in to read it.\n\n" +
            "${policy.publicOrigin}/app/${policy.appEnvironment}/accounts/${dispatch.accountId}/companies/${dispatch.companyId}/inbox/${dispatch.inboxId}\n",
    )

fun inboxEmailRetryAt(
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
    return now.plusSeconds(maxOf(backoff, providerDelay)).takeIf { it.isBefore(expiresAt) }
}

fun Result<Boolean>.requireEmailLease(): Result<Unit> = flatMap {
    if (it) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.CONFLICT, "email_lease_lost"))
}

fun inboxEmailFinishedRecord(dispatch: InboxEmailDispatch, state: InboxEmailState, code: String?) =
    ChangeRecord(
        "inbox_item",
        dispatch.inboxId,
        "communications.email_${state.name.lowercase()}",
        mapOf("code" to (code ?: "accepted")),
    )
