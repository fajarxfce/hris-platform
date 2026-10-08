package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.mail.domain.entities.OutboundMail
import java.time.Instant

fun credentialChallengeUsable(
    challenge: CredentialChallenge,
    account: CredentialAccount,
    now: Instant,
): Boolean =
    challenge.accountId == account.id &&
        challenge.credentialVersion == account.securityVersion &&
        challenge.consumedAt == null &&
        challenge.revokedAt == null &&
        !now.isBefore(challenge.issuedAt) &&
        now.isBefore(challenge.expiresAt) &&
        when (challenge.kind) {
            CredentialChallengeKind.INVITATION ->
                account.invitationPending && !account.active && !account.hasPassword
            CredentialChallengeKind.PASSWORD_RECOVERY ->
                account.active && !account.invitationPending && account.hasPassword
        }

fun credentialMail(
    challenge: CredentialChallenge,
    account: CredentialAccount,
    token: String,
    policy: CredentialChallengePolicy,
): OutboundMail {
    val invitation = challenge.kind == CredentialChallengeKind.INVITATION
    val page = if (invitation) "accept-invitation" else "reset-password"
    val subject = if (invitation) "Set up your HRIS account" else "Reset your HRIS password"
    return OutboundMail(
        challenge.id,
        account.email,
        subject,
        "${if(invitation) "Set your password to activate your account." else "Use this link to reset your password."}\n\n" +
            "${policy.publicOrigin}/auth/$page#token=$token\n\n" +
            "This link can be used once. If you did not expect this email, contact your administrator.",
    )
}

fun identityMailOutcome(
    attempts: Int,
    failure: Failure?,
    now: Instant,
    expiresAt: Instant,
    maximumAttempts: Int,
): IdentityMailOutcome {
    if (failure == null) return IdentityMailOutcome(IdentityMailState.SENT, now, null, true)
    val delay = 5L shl (attempts - 1).coerceIn(0, 6)
    val retryAt = now.plusSeconds(delay.coerceAtMost(300))
    if (
        failure.kind == FailureKind.UNAVAILABLE &&
            attempts < maximumAttempts &&
            retryAt.isBefore(expiresAt)
    )
        return IdentityMailOutcome(IdentityMailState.PENDING, retryAt, failure.code, false)
    return IdentityMailOutcome(IdentityMailState.FAILED, now, failure.code, true)
}
