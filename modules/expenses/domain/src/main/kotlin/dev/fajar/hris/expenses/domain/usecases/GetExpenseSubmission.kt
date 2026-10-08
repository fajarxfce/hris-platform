package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class GetExpenseSubmission(
    private val claims: ExpenseClaimRepository,
    private val approvals: ApprovalRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<ExpenseSubmissionDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val found = claims.submission(company, id)
            if (found is Result.Failed) return@run found
            val submission =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_submission_not_found")
                    )
            val foundClaim = claims.find(company, submission.claimId)
            if (foundClaim is Result.Failed) return@run foundClaim
            val claim =
                (foundClaim as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_claim_not_found")
                    )
            val foundApproval = approvals.find(company, submission.approvalId)
            if (foundApproval is Result.Failed) return@run foundApproval
            val approval =
                (foundApproval as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "approval_unavailable")
                    )
            val foundCompany = companies.find(company)
            if (foundCompany is Result.Failed) return@run foundCompany
            val settings =
                (foundCompany as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val foundEmployee = people.findAtInstant(company, claim.employmentId, now)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee = (foundEmployee as Result.Success).value
            if (employee == null || !canReadExpenseClaim(live, employee, today)) {
                val delegated = approvals.delegations(company, actor.accountId, now)
                if (delegated is Result.Failed) return@run delegated
                val assigned = approval.stages.flatMap { it.assignees }.toSet()
                val grants = members.candidates(company, assigned, emptySet(), assigned.size)
                if (grants is Result.Failed) return@run grants
                if (
                    !isAssignedApprover(
                        live,
                        approval,
                        (delegated as Result.Success).value,
                        (grants as Result.Success).value,
                        now,
                    )
                )
                    return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_submission_not_found")
                    )
            }
            val foundReviews = claims.reviews(company, id)
            if (foundReviews is Result.Failed) return@run foundReviews
            val hashes = submission.lines.flatMap { it.receipts }.map { it.sha256 }.toSet()
            val foundDuplicates = claims.duplicateReceiptDigests(company, claim.id, hashes)
            if (foundDuplicates is Result.Failed) return@run foundDuplicates
            val duplicates =
                duplicateExpenseReceiptDigests(
                    submission.lines,
                    (foundDuplicates as Result.Success).value,
                )
            Result.Success(
                ExpenseSubmissionDetails(
                    submission,
                    claim,
                    approval,
                    (foundReviews as Result.Success).value,
                    duplicates,
                )
            )
        }
    }
}
