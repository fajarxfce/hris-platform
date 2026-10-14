package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.canReadExpenseClaim
import dev.fajar.hris.expenses.domain.repositories.ExpenseClaimRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

class GetExpenseClaimRecord(
    private val claims: ExpenseClaimRepository,
    private val approvals: ApprovalRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        historyAfter: Long?,
        historyLimit: Int,
        scope: ExpenseClaimReadScope = ExpenseClaimReadScope.SELF,
    ): Result<ExpenseClaimRecord> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (historyLimit !in 1..20 || (historyAfter != null && historyAfter < 0))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val resource = claims.lock(company, shared = true)
            if (resource is Result.Failed) return@run resource
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val approvalGuard = approvals.lock(company, shared = true)
            if (approvalGuard is Result.Failed) return@run approvalGuard
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
            val zone = ZoneId.of(settings.timezone)
            val now = clock.instant()
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
            if (
                employee == null ||
                    !canReadExpenseClaim(live, employee, now.atZone(zone).toLocalDate(), scope)
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "expense_claim_not_found"))
            // Relative cursor validation follows resource admission to avoid a version oracle.
            if (historyAfter != null && historyAfter > claim.version)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            val draftResult = claims.draft(company, id, claim.draftRevision)
            if (draftResult is Result.Failed) return@run draftResult
            val draft =
                (draftResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.UNEXPECTED, "expense_draft_missing")
                    )
            val submitted = loadLatestSubmission(company, claim)
            if (submitted is Result.Failed) return@run submitted
            val history = claims.history(company, id, historyAfter, historyLimit)
            if (history is Result.Failed) return@run history
            val deliveredAt = clock.instant()
            validateCompanySessionActor(actor, access, deliveredAt, security).flatMap { admitted ->
                if (
                    !canReadExpenseClaim(
                        admitted,
                        employee,
                        deliveredAt.atZone(zone).toLocalDate(),
                        scope,
                    )
                )
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "expense_claim_not_found"))
                else
                    Result.Success(
                        ExpenseClaimRecord(
                            ExpenseClaimDetails(claim, draft),
                            (submitted as Result.Success).value,
                            (history as Result.Success).value,
                        )
                    )
            }
        }
    }

    /** Read the latest submitted evidence and current approval under their shared guards. */
    private fun loadLatestSubmission(
        company: UUID,
        claim: ExpenseClaim,
    ): Result<ExpenseSubmissionRecord?> {
        val id = claim.latestSubmissionId ?: return Result.Success(null)
        val found = claims.submission(company, id)
        if (found is Result.Failed) return found
        val submission =
            (found as Result.Success).value
                ?: return Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "expense_submission_missing")
                )
        if (submission.claimId != claim.id)
            return Result.Failed(Failure(FailureKind.UNEXPECTED, "expense_submission_invalid"))
        val approvalResult = approvals.find(company, submission.approvalId)
        if (approvalResult is Result.Failed) return approvalResult
        val approval =
            (approvalResult as Result.Success).value
                ?: return Result.Failed(Failure(FailureKind.CONFLICT, "approval_unavailable"))
        val reviews = claims.reviews(company, id)
        if (reviews is Result.Failed) return reviews
        return Result.Success(
            ExpenseSubmissionRecord(submission, approval, (reviews as Result.Success).value)
        )
    }
}
