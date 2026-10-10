package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.LeavePolicyReview
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import java.time.Clock
import java.util.UUID

class GetLeavePolicy(
    private val policies: LeavePolicyRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        historyAfter: Long?,
        historyLimit: Int,
    ): Result<LeavePolicyReview> {
        val initial = actor.requirePermission("leave.manage")
        if (initial is Result.Failed) return initial
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (historyLimit !in 1..200 || (historyAfter != null && historyAfter < 0))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (checked is Result.Failed) return@run checked
            val permission = (checked as Result.Success).value.requirePermission("leave.manage")
            if (permission is Result.Failed) return@run permission
            val found = policies.find(company, id)
            if (found is Result.Failed) return@run found
            val current =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_type_not_found")
                    )
            if (historyAfter != null && historyAfter > current.version)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            policies.history(company, id, historyAfter, historyLimit).map {
                LeavePolicyReview(current, it)
            }
        }
    }
}
