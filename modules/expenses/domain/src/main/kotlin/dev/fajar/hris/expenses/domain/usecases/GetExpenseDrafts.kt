package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class GetExpenseDrafts(
    private val claims: ExpenseClaimRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val members: MembershipRepository,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        after: Int?,
        limit: Int,
        scope: ExpenseClaimReadScope = ExpenseClaimReadScope.AUTHORIZED,
    ): Result<Page<ExpenseDraft>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (limit !in 1..20 || (after != null && after !in 0..99))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val resource = claims.lock(company, shared = true)
            if (resource is Result.Failed) return@run resource
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
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
            val live = (current as Result.Success).value

            val foundCompany = companies.find(company)
            if (foundCompany is Result.Failed) return@run foundCompany
            val settings =
                (foundCompany as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()

            val found = claims.find(company, id)
            if (found is Result.Failed) return@run found
            val claim =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_claim_not_found")
                    )
            val foundEmployee = people.find(company, claim.employmentId, today)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee = (foundEmployee as Result.Success).value
            if (employee == null || !canReadExpenseClaim(live, employee, today, scope))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "expense_claim_not_found"))

            val acquired = claims.drafts(company, id, after, limit)
            acquired.flatMap { value ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map { value }
            }
        }
    }
}
