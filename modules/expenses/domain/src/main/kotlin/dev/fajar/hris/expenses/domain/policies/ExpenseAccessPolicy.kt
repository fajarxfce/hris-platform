package dev.fajar.hris.expenses.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess

fun validateExpenseActor(actor: Actor, access: AccountAccess?): Result<Actor> {
    if (
        access == null ||
            !access.account.active ||
            (actor.credentialVersion != null &&
                actor.credentialVersion != access.account.securityVersion)
    )
        return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
    if (!access.companyActive || !access.membershipActive)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_access_denied"))
    return Result.Success(actor.copy(permissions = access.permissions))
}

fun canReadExpensePolicies(actor: Actor): Boolean =
    actor.permissions.any {
        it in
            setOf(
                "expenses.policy.manage",
                "expenses.read",
                "expenses.approve",
                "expenses.pay",
                "expenses.team.read",
                "expenses.team.approve",
                "expenses.self.manage",
                "expenses.manage",
            )
    }
