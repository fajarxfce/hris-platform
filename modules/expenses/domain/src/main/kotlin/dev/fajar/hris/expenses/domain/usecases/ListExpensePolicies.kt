package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseCategory
import dev.fajar.hris.expenses.domain.repositories.ExpensePolicyRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock

/**
 * Administrative heads include future and archived definitions, independently of effective
 * selection.
 */
class ListExpensePolicies(
    private val policies: ExpensePolicyRepository,
    private val identities: IdentityRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        active: Boolean?,
        after: String?,
        limit: Int,
    ): Result<Page<ExpenseCategory>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val initial = actor.requirePermission("expenses.policy.manage")
        if (initial is Result.Failed) return initial
        if (limit !in 1..200 || (after != null && !after.matches(Regex("[A-Z][A-Z0-9_]{1,31}"))))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val resource = policies.lock(company, shared = true)
            if (resource is Result.Failed) return@run resource
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val accessed = identities.access(actor.accountId, company)
            if (accessed is Result.Failed) return@run accessed
            val access = (accessed as Result.Success).value
            val current = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (current is Result.Failed) return@run current
            val permission =
                (current as Result.Success).value.requirePermission("expenses.policy.manage")
            if (permission is Result.Failed) return@run permission
            policies.catalog(company, active, after, limit).flatMap { value ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map { value }
            }
        }
    }
}
