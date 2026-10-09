package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess

/** Revalidation may remove a platform permission, never add one to an admitted request. */
fun validatePlatformCommandActor(actor: Actor, access: AccountAccess?): Result<Actor> {
    if (actor.companyId != null)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
    if (
        access == null ||
            access.account.id != actor.accountId ||
            !access.account.active ||
            (actor.credentialVersion != null &&
                actor.credentialVersion != access.account.securityVersion)
    )
        return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
    return Result.Success(
        actor.copy(
            permissions = actor.permissions intersect access.permissions,
            platformPermissions = actor.platformPermissions intersect access.platformPermissions,
        )
    )
}
