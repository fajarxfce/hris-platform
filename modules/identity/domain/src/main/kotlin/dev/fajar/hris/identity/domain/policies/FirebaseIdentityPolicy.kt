package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.time.Duration
import java.time.Instant

/** Provider identity grants no HRIS role, company access or MFA assurance. */
fun validateFirebaseIdentity(
    identity: FirebaseIdentity,
    policy: FirebaseSignInPolicy,
    now: Instant,
): Result<FirebaseIdentity> {
    if (
        !policy.enabled ||
            identity.issuer != policy.issuer ||
            identity.audience != policy.projectId ||
            identity.subject.length !in 1..128 ||
            identity.subject.any(Char::isISOControl) ||
            identity.subject.isBlank() ||
            identity.signInProvider !in policy.signInProviders ||
            identity.tenantId != null ||
            identity.issuedAt > now ||
            identity.authenticatedAt > now ||
            identity.authenticatedAt > identity.issuedAt ||
            identity.expiresAt <= now ||
            identity.expiresAt <= identity.issuedAt ||
            identity.expiresAt > identity.issuedAt.plus(Duration.ofHours(1)) ||
            identity.authenticatedAt < now.minus(policy.recentAuthenticationAge)
    )
        return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "firebase_sign_in_failed"))
    return Result.Success(identity)
}
