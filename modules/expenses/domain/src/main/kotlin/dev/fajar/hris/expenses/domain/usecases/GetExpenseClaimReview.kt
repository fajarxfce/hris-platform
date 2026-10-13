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

class GetExpenseClaimReview(
    private val claims: ExpenseClaimRepository,
    private val payments: ExpensePaymentRepository,
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
        historyAfter: Long?,
        historyLimit: Int,
        submissionsAfter: Int?,
        submissionsLimit: Int,
        scope: ExpenseClaimReadScope = ExpenseClaimReadScope.AUTHORIZED,
    ): Result<ExpenseClaimReview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (
            historyLimit !in 1..200 ||
                (historyAfter != null && historyAfter < 0) ||
                submissionsLimit !in 1..20 ||
                (submissionsAfter != null && submissionsAfter !in 1..20)
        )
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
            val foundEmployee = people.findAtInstant(company, claim.employmentId, now)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee = (foundEmployee as Result.Success).value
            if (employee == null || !canReadExpenseClaim(live, employee, today, scope))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "expense_claim_not_found"))

            if (
                (historyAfter != null && historyAfter > claim.version) ||
                    (submissionsAfter != null && submissionsAfter > claim.submissionCount)
            )
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))

            val draftResult = claims.draft(company, id, claim.draftRevision)
            if (draftResult is Result.Failed) return@run draftResult
            val draft =
                (draftResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.UNEXPECTED, "expense_draft_missing")
                    )
            val history = claims.history(company, id, historyAfter, historyLimit)
            if (history is Result.Failed) return@run history
            val submissions = claims.submissions(company, id, submissionsAfter, submissionsLimit)
            if (submissions is Result.Failed) return@run submissions
            val progress = payments.progress(company, id)
            if (progress is Result.Failed) return@run progress
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                ExpenseClaimReview(
                    ExpenseClaimDetails(claim, draft),
                    (history as Result.Success).value,
                    (submissions as Result.Success).value,
                    (progress as Result.Success).value,
                )
            }
        }
    }
}
