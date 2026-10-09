package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.time.Duration
import java.time.Instant

fun requiresMfa(permissions: Set<String>): Boolean =
    permissions.any { it !in PermissionCatalog.employee && it != "announcements.read" }

fun validateSessionAssurance(
    account: Account,
    permissions: Set<String>,
    verifiedAt: Instant?,
    now: Instant,
    policy: IdentitySecurityPolicy,
): Result<Unit> {
    if (!policy.enforceMfa || (!requiresMfa(permissions) && !account.mfaConfigured))
        return Result.Success(Unit)
    if (!account.mfaConfigured)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "mfa_setup_required"))
    if (
        verifiedAt == null ||
            verifiedAt.isAfter(now) ||
            !verifiedAt.plus(policy.maximumMfaAge).isAfter(now)
    )
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "mfa_required"))
    return Result.Success(Unit)
}

fun requireRecentAuthentication(actor: Actor, now: Instant, maximumAge: Duration): Result<Unit> =
    if (actor.authenticatedAt.isAfter(now) || !actor.authenticatedAt.plus(maximumAge).isAfter(now))
        Result.Failed(Failure(FailureKind.FORBIDDEN, "recent_authentication_required"))
    else Result.Success(Unit)

fun requireRecentMfa(actor: Actor, now: Instant, maximumAge: Duration): Result<Unit> {
    val proof = actor.mfaVerifiedAt
    return if (proof == null || proof.isAfter(now) || !proof.plus(maximumAge).isAfter(now))
        Result.Failed(Failure(FailureKind.FORBIDDEN, "recent_authentication_required"))
    else Result.Success(Unit)
}

/** Global credential administration requires a recent applicable authentication proof. */
fun requireRecentIdentityAdministration(
    actor: Actor,
    now: Instant,
    policy: IdentitySecurityPolicy,
): Result<Unit> =
    if (policy.enforceMfa) requireRecentMfa(actor, now, policy.recentAuthenticationAge)
    else requireRecentAuthentication(actor, now, policy.recentAuthenticationAge)

fun validateMfaCredential(actor: Actor, credential: MfaCredential?): Result<Unit> =
    if (
        credential == null ||
            !credential.account.active ||
            credential.account.securityVersion != actor.credentialVersion
    )
        Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
    else Result.Success(Unit)

fun mfaAttemptWindow(at: Instant, policy: IdentitySecurityPolicy): Instant =
    Instant.ofEpochSecond(
        Math.floorDiv(at.epochSecond, policy.mfaAttemptWindowSeconds) *
            policy.mfaAttemptWindowSeconds
    )
