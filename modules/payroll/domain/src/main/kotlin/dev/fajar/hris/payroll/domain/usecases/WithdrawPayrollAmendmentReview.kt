package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class WithdrawPayrollAmendmentReview(
    private val amendments: PayrollAmendmentRepository,
    private val reviews: PayrollAmendmentReviewRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        amendmentId: UUID,
        id: UUID,
        expectedVersion: Long,
        expectedReviewVersion: Long,
        expectedApprovalVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = requirePayrollMutation(actor, "payroll.amend", clock.instant(), security)
        if (permission is Result.Failed) return permission
        val valid = validatePayrollAmendmentCommand(expectedVersion, reason)
        if (valid is Result.Failed) return valid
        if (expectedReviewVersion !in 0..9 || expectedApprovalVersion !in 0 until Long.MAX_VALUE)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "payroll.amendment_review_withdraw",
                operationId,
                listOf(
                    amendmentId.toString(),
                    id.toString(),
                    expectedVersion.toString(),
                    expectedReviewVersion.toString(),
                    expectedApprovalVersion.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val payrollGuard = policies.lock(company)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val approvalGuard = approvals.lock(company)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed = requirePayrollMutation(live, "payroll.amend", clock.instant(), security)
            if (allowed is Result.Failed) return@run allowed
            val found = amendments.find(company, amendmentId)
            if (found is Result.Failed) return@run found
            val current =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_not_found")
                    )
            val owner = people.accountForEmployee(company, current.summary.employee.id)
            if (owner is Result.Failed) return@run owner
            if ((owner as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_amendment_self_preparation_forbidden")
                )
            (replay as Result.Success).value?.let { receipt ->
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .flatMap {
                        requirePayrollMutation(it, "payroll.amend", clock.instant(), security).map {
                            receipt
                        }
                    }
            }
            val foundReview = reviews.find(company, id)
            if (foundReview is Result.Failed) return@run foundReview
            val review =
                (foundReview as Result.Success).value?.takeIf { it.amendmentId == amendmentId }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_review_not_found")
                    )
            if (
                current.summary.version != expectedVersion ||
                    review.version != expectedReviewVersion
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                review.status !in
                    setOf(
                        PayrollAmendmentReviewStatus.PENDING,
                        PayrollAmendmentReviewStatus.APPROVED,
                    )
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_review_not_active")
                )
            if (
                current.summary.latestReviewId != id ||
                    current.summary.latestCalculationId != review.calculationId ||
                    current.summary.status !in
                        setOf(PayrollAmendmentStatus.IN_REVIEW, PayrollAmendmentStatus.APPROVED)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_review_not_current")
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
            val now = clock.instant()
            val outcome =
                current.copy(
                    summary =
                        current.summary.copy(
                            version = expectedVersion + 1,
                            status = PayrollAmendmentStatus.CALCULATED,
                            action = PayrollAmendmentAction.WITHDRAWN,
                            actorId = actor.accountId,
                            recordedAt = now,
                            reason = reason,
                        )
                )
            approvals
                .cancel(company, approval.id, approval.version)
                .flatMap { cancelled ->
                    reviews.transition(
                        company,
                        review,
                        PayrollAmendmentReviewChange(
                            review.version + 1,
                            expectedVersion + 1,
                            PayrollAmendmentAction.WITHDRAWN,
                            PayrollAmendmentReviewStatus.WITHDRAWN,
                            cancelled.version,
                            null,
                            actor.accountId,
                            null,
                            reason,
                            now,
                        ),
                    )
                }
                .flatMap { amendments.save(company, outcome, expectedVersion) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_amendment",
                                amendmentId,
                                "payroll.amendment_review_withdrawn",
                                mapOf("reviewId" to id.toString()),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            validateCompanySessionActor(actor, access, clock.instant(), security)
                        }
                        .flatMap {
                            requirePayrollMutation(it, "payroll.amend", clock.instant(), security)
                                .map { receipt }
                        }
                }
        }
    }
}
