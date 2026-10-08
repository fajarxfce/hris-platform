package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.approvals.domain.entities.ApprovalStatus
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class WithdrawExpenseSubmission(
    private val claims: ExpenseClaimRepository,
    private val approvals: ApprovalRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_expense_action"))
        val key =
            OperationKey(
                "expenses.submission_withdraw",
                operationId,
                listOf(id.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val claimLock = claims.lock(company)
            if (claimLock is Result.Failed) return@run claimLock
            val peopleLock = people.lockReportingLines(company)
            if (peopleLock is Result.Failed) return@run peopleLock
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val foundSubmission = claims.submission(company, id)
            if (foundSubmission is Result.Failed) return@run foundSubmission
            val submission =
                (foundSubmission as Result.Success).value
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
            val now = clock.instant()
            val foundEmployee = people.findAtInstant(company, claim.employmentId, now)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee = (foundEmployee as Result.Success).value
            if (employee == null || !canManageExpenseClaim(live, employee))
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "expense_submission_not_found")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (claim.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (claim.status != ExpenseClaimStatus.PENDING || claim.latestSubmissionId != id)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_submission_not_current")
                )
            val foundApproval = approvals.find(company, submission.approvalId)
            if (foundApproval is Result.Failed) return@run foundApproval
            val approval =
                (foundApproval as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "approval_unavailable")
                    )
            if (approval.status !in setOf(ApprovalStatus.PENDING, ApprovalStatus.BLOCKED))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            approvals
                .cancel(company, approval.id, approval.version)
                .flatMap { claims.withdraw(actor, claim, reason, now) }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "expense_claim",
                                    claim.id,
                                    "expenses.submission_withdrawn",
                                    mapOf(
                                        "submissionId" to id.toString(),
                                        "approvalId" to approval.id.toString(),
                                    ),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
