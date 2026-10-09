package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess

/**
 * Revalidates an already resolved request against live company access. Permissions may be removed
 * while a command waits, but newly granted capabilities require a new request and its
 * session-assurance checks. Background jobs must resolve their own execution authority.
 */
fun validateCompanyCommandActor(actor: Actor, access: AccountAccess?): Result<Actor> {
    if (actor.companyId == null)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
    if (
        access == null ||
            access.account.id != actor.accountId ||
            !access.account.active ||
            (actor.credentialVersion != null &&
                actor.credentialVersion != access.account.securityVersion)
    )
        return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
    if (!access.companyActive || !access.membershipActive)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_access_denied"))
    return Result.Success(
        actor.copy(
            permissions = actor.permissions intersect access.permissions,
            platformPermissions = actor.platformPermissions intersect access.platformPermissions,
        )
    )
}
