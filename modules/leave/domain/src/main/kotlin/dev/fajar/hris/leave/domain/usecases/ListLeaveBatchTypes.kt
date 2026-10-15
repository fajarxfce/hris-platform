package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import java.time.Clock

/** Current labels include archived types; preparation selects the period's effective policy. */
class ListLeaveBatchTypes(
    private val policies: LeavePolicyRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        kind: LeaveBatchKind,
        after: String?,
        limit: Int,
    ): Result<Page<LeaveType>> {
        val initial = actor.requirePermission(kind.permission)
        if (initial is Result.Failed) return initial
        if (limit !in 1..50 || (after != null && !after.matches(Regex("[A-Z][A-Z0-9_-]{0,31}"))))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val admitted = (checked as Result.Success).value.requirePermission(kind.permission)
            if (admitted is Result.Failed) return@run admitted
            val acquired = policies.catalog(company, null, after, limit)
            if (acquired is Result.Failed) return@run acquired
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                (acquired as Result.Success).value
            }
        }
    }
}
