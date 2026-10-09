package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.requireRecentAuthentication
import dev.fajar.hris.identity.domain.policies.requireRecentMfa
import java.time.Instant

fun requirePayrollMutation(
    actor: Actor,
    permission: String,
    now: Instant,
    security: IdentitySecurityPolicy,
): Result<Unit> {
    val access = actor.requirePermission(permission)
    if (access is Result.Failed) return access
    val recent = requireRecentAuthentication(actor, now, security.recentAuthenticationAge)
    if (recent is Result.Failed) return recent
    return if (security.enforceMfa) requireRecentMfa(actor, now, security.recentAuthenticationAge)
    else Result.Success(Unit)
}

fun canReadPayrollPolicy(actor: Actor): Boolean =
    actor.permissions.any {
        it in setOf("payroll.read", "payroll.policy.manage", "payroll.compensation.manage")
    }

fun canReadCompensation(actor: Actor): Boolean =
    "payroll.read" in actor.permissions || "payroll.compensation.manage" in actor.permissions
