package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePaymentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class GetExpensePaymentHistory(
    private val payments: ExpensePaymentRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<ExpensePaymentAction>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (limit !in 1..200 || (after != null && after < 0))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val access = validateExpensePaymentAccess(actor, clock.instant(), security)
        if (access is Result.Failed) return access
        return transactions.run(actor) {
            val resource = payments.lock(company, shared = true)
            if (resource is Result.Failed) return@run resource
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val recent =
                validateExpensePaymentAccess(
                    (checked as Result.Success).value,
                    clock.instant(),
                    security,
                )
            if (recent is Result.Failed) return@run recent
            val found = payments.find(company, id)
            if (found is Result.Failed) return@run found
            if ((found as Result.Success).value == null)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "expense_payment_batch_not_found")
                )
            payments.history(company, id, after, limit)
        }
    }
}
