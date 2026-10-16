package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.RoleTemplateReview
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetRoleTemplate(
    private val roles: RoleTemplateRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        historyAfter: Long?,
        historyLimit: Int,
    ): Result<RoleTemplateReview> {
        val initial = actor.requirePermission("identity.manage")
        if (initial is Result.Failed) return initial
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (historyLimit !in 1..200 || (historyAfter != null && historyAfter < 0))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val roleGuard = roles.lock(company, shared = true)
            if (roleGuard is Result.Failed) return@run roleGuard
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val accessed = identities.access(actor.accountId, company)
            if (accessed is Result.Failed) return@run accessed
            val access = (accessed as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val permission = (checked as Result.Success).value.requirePermission("identity.manage")
            if (permission is Result.Failed) return@run permission
            val found = roles.find(company, id)
            if (found is Result.Failed) return@run found
            val current =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "role_template_not_found")
                    )
            if (historyAfter != null && historyAfter > current.version)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            roles.history(company, id, historyAfter, historyLimit).flatMap { history ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map {
                    RoleTemplateReview(current, history)
                }
            }
        }
    }
}
