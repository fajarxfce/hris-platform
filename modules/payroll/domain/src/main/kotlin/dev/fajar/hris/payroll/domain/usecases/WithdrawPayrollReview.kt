package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class WithdrawPayrollReview(
    private val reviews: PayrollReviewRepository,
    private val runs: PayrollRunRepository,
    private val policies: PayrollPolicyRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        expectedApprovalVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = actor.requirePermission("payroll.calculate")
        if (permission is Result.Failed) return permission
        if (
            expectedVersion !in 0..9 ||
                expectedApprovalVersion < 0 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_review"))
        val key =
            OperationKey(
                "payroll.review_withdraw",
                operationId,
                listOf(
                    id.toString(),
                    expectedVersion.toString(),
                    expectedApprovalVersion.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = policies.lock(company)
            if (guard is Result.Failed) return@run guard
            val approvalGuard = approvals.lock(company)
            if (approvalGuard is Result.Failed) return@run approvalGuard
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
            val live = (checked as Result.Success).value
            val access =
                requirePayrollMutation(live, "payroll.calculate", clock.instant(), security)
            if (access is Result.Failed) return@run access
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = reviews.find(company, id)
            if (found is Result.Failed) return@run found
            val review =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_review_not_found")
                    )
            if (review.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (review.status !in setOf(PayrollReviewStatus.PENDING, PayrollReviewStatus.APPROVED))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_review_not_active"))
            val foundRun = runs.find(company, review.runId)
            if (foundRun is Result.Failed) return@run foundRun
            val run = (foundRun as Result.Success).value
            if (run?.status != PayrollRunStatus.CALCULATED || run.version != review.runVersion)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_review_not_current")
                )
            val foundApproval = approvals.find(company, review.approvalId)
            if (foundApproval is Result.Failed) return@run foundApproval
            val approval =
                (foundApproval as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "approval_unavailable")
                    )
            if (approval.version != expectedApprovalVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            approvals
                .cancel(company, approval.id, approval.version)
                .flatMap { cancelled ->
                    reviews.transition(
                        company,
                        review,
                        PayrollReviewChange(
                            review.version + 1,
                            PayrollReviewAction.WITHDRAWN,
                            PayrollReviewStatus.WITHDRAWN,
                            cancelled.version,
                            null,
                            actor.accountId,
                            null,
                            reason,
                            clock.instant(),
                        ),
                    )
                }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_review",
                                id,
                                "payroll.review_withdrawn",
                                mapOf("runId" to run.id.toString()),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .map { receipt }
                }
        }
    }
}
