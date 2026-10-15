package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class StartPayrollAmendmentPublication(
    private val publications: PayrollAmendmentPublicationRepository,
    private val amendments: PayrollAmendmentRepository,
    private val reviews: PayrollAmendmentReviewRepository,
    private val sources: PayrollAmendmentSourceRepository,
    private val policies: PayrollPolicyRepository,
    private val payments: PayrollPaymentRepository,
    private val approvals: ApprovalRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val operations: OperationRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        amendmentId: UUID,
        id: UUID,
        expectedVersion: Long,
        reviewId: UUID,
        expectedReviewVersion: Long,
        expectedApprovalVersion: Long,
        calculationId: UUID,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permitted =
            requirePayrollMutation(actor, "payroll.amend.publish", clock.instant(), security)
        if (permitted is Result.Failed) return permitted
        val valid = validatePayrollAmendmentCommand(expectedVersion, reason)
        if (valid is Result.Failed) return valid
        if (expectedReviewVersion !in 1..8 || expectedApprovalVersion < 1)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_payroll_amendment_publication")
            )
        val key =
            OperationKey(
                "payroll.amendment_publication_start",
                operationId,
                listOf(
                    amendmentId.toString(),
                    id.toString(),
                    expectedVersion.toString(),
                    reviewId.toString(),
                    expectedReviewVersion.toString(),
                    expectedApprovalVersion.toString(),
                    calculationId.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val payrollGuard = policies.lock(company)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val paymentGuard = payments.lock(company)
            if (paymentGuard is Result.Failed) return@run paymentGuard
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
            val acquiredAccess = identities.access(actor.accountId, company)
            if (acquiredAccess is Result.Failed) return@run acquiredAccess
            val access = (acquiredAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed =
                requirePayrollMutation(live, "payroll.amend.publish", clock.instant(), security)
            if (allowed is Result.Failed) return@run allowed
            val found = amendments.find(company, amendmentId)
            if (found is Result.Failed) return@run found
            val current =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_not_found")
                    )
            val foundReview = reviews.find(company, reviewId)
            if (foundReview is Result.Failed) return@run foundReview
            val review =
                (foundReview as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_review_not_found")
                    )
            if (review.amendmentId != amendmentId)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "payroll_amendment_review_not_found")
                )
            val foundApproval = approvals.find(company, review.approvalId)
            if (foundApproval is Result.Failed) return@run foundApproval
            val approval =
                (foundApproval as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_amendment_approved_review_required")
                    )
            val owner = people.accountForEmployee(company, current.summary.employee.id)
            if (owner is Result.Failed) return@run owner
            if (
                (owner as Result.Success).value == actor.accountId ||
                    approval.requesterId == actor.accountId
            )
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_amendment_self_preparation_forbidden")
                )
            (replay as Result.Success).value?.let { receipt ->
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .flatMap {
                        requirePayrollMutation(
                            it,
                            "payroll.amend.publish",
                            clock.instant(),
                            security,
                        )
                    }
                    .map { receipt }
            }
            if (
                current.summary.version != expectedVersion ||
                    review.version != expectedReviewVersion
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (approval.version != expectedApprovalVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            val attempts = publications.list(company, amendmentId)
            if (attempts is Result.Failed) return@run attempts
            val next =
                nextPayrollAmendmentPublication(
                    current.summary,
                    (attempts as Result.Success).value.lastOrNull(),
                )
            if (next is Result.Failed) return@run next
            val foundCalculation = amendments.calculation(company, amendmentId, calculationId)
            if (foundCalculation is Result.Failed) return@run foundCalculation
            val calculation =
                (foundCalculation as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_calculation_not_found")
                    )
            val approved =
                requireApprovedPayrollAmendment(current.summary, review, approval, calculation)
            if (approved is Result.Failed) return@run approved
            val preview = calculation.preview!!
            val acquiredSources = sources.sources(company, current.summary.firstAssessmentId)
            if (acquiredSources is Result.Failed) return@run acquiredSources
            val fresh =
                requirePayrollAmendmentPublicationSources(
                    current,
                    preview,
                    (acquiredSources as Result.Success).value,
                )
            if (fresh is Result.Failed) return@run fresh
            val reserved =
                payments.occupiedAssessments(
                    company,
                    preview.calculations.map { it.source.assessmentId }.toSet(),
                )
            if (reserved is Result.Failed) return@run reserved
            if ((reserved as Result.Success).value.isNotEmpty())
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_payment_pending")
                )
            val queueGuard = jobs.lockQueue(company)
            if (queueGuard is Result.Failed) return@run queueGuard
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val admission =
                validateCompanySessionActor(actor, access, clock.instant(), security).flatMap {
                    requirePayrollMutation(it, "payroll.amend.publish", clock.instant(), security)
                }
            if (admission is Result.Failed) return@run admission
            val now = clock.instant()
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.PAYROLL_AMENDMENT_PUBLISH,
                    operationId,
                    mapOf(
                        "publicationId" to id.toString(),
                        "amendmentId" to amendmentId.toString(),
                    ),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    1,
                )
            val publication =
                PayrollAmendmentPublication(
                    id,
                    amendmentId,
                    reviewId,
                    calculationId,
                    expectedVersion,
                    expectedReviewVersion,
                    expectedApprovalVersion,
                    (next as Result.Success).value,
                    job.id,
                    actor.accountId,
                    now,
                    reason,
                )
            val admitted =
                current.copy(
                    summary =
                        current.summary.copy(
                            version = expectedVersion + 1,
                            status = PayrollAmendmentStatus.PUBLISHING,
                            action = PayrollAmendmentAction.PUBLICATION_STARTED,
                            latestPublicationId = id,
                            actorId = actor.accountId,
                            recordedAt = now,
                            reason = reason,
                        )
                )
            jobs
                .create(job)
                .flatMap { publications.create(company, publication) }
                .flatMap { amendments.save(company, admitted, expectedVersion) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_amendment",
                                amendmentId,
                                "payroll.amendment_publication_started",
                                mapOf(
                                    "publicationId" to id.toString(),
                                    "jobId" to job.id.toString(),
                                ),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            validateCompanySessionActor(actor, access, clock.instant(), security)
                        }
                        .flatMap {
                            requirePayrollMutation(
                                it,
                                "payroll.amend.publish",
                                clock.instant(),
                                security,
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
