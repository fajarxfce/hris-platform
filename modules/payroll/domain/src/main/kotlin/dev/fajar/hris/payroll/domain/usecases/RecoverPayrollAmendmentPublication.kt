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

class RecoverPayrollAmendmentPublication(
    private val publications: PayrollAmendmentPublicationRepository,
    private val amendments: PayrollAmendmentRepository,
    private val reviews: PayrollAmendmentReviewRepository,
    private val policies: PayrollPolicyRepository,
    private val approvals: ApprovalRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        amendmentId: UUID,
        id: UUID,
        expectedVersion: Long,
        expectedJobVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission =
            requirePayrollMutation(actor, "payroll.amend.publish", clock.instant(), security)
        if (permission is Result.Failed) return permission
        val valid = validatePayrollAmendmentCommand(expectedVersion, reason)
        if (valid is Result.Failed) return valid
        if (expectedJobVersion < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_job_version"))
        val key =
            OperationKey(
                "payroll.amendment_publication_recover",
                operationId,
                listOf(
                    amendmentId.toString(),
                    id.toString(),
                    expectedVersion.toString(),
                    expectedJobVersion.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            // The immutable job pointer is observed before locking; workers always own the job
            // first.
            val observed = publications.find(company, amendmentId, id)
            if (observed is Result.Failed) return@run observed
            val publication =
                (observed as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_publication_not_found")
                    )
            val foundJob = jobs.find(company, publication.jobId, lock = true)
            if (foundJob is Result.Failed) return@run foundJob
            val job =
                (foundJob as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_amendment_publication_obsolete")
                    )
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
            val acquiredAccess = identities.access(actor.accountId, company)
            if (acquiredAccess is Result.Failed) return@run acquiredAccess
            val access = (acquiredAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed =
                requirePayrollMutation(live, "payroll.amend.publish", clock.instant(), security)
            if (allowed is Result.Failed) return@run allowed
            val reviewId = publication.reviewId
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
            if (current.summary.version != expectedVersion || job.version != expectedJobVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                publication.status != PayrollAmendmentPublicationStatus.PENDING ||
                    current.summary.status != PayrollAmendmentStatus.PUBLISHING ||
                    current.summary.latestPublicationId != id ||
                    current.summary.version != publication.amendmentVersion + 1 ||
                    job.completedItems != 0 ||
                    job.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED) ||
                    job.request.kind != JobKind.PAYROLL_AMENDMENT_PUBLISH ||
                    job.request.values["publicationId"] != id.toString()
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_recovery_unavailable")
                )
            val now = clock.instant()
            val restored =
                current.copy(
                    summary =
                        current.summary.copy(
                            version = expectedVersion + 1,
                            status = PayrollAmendmentStatus.APPROVED,
                            action = PayrollAmendmentAction.PUBLICATION_STOPPED,
                            actorId = actor.accountId,
                            recordedAt = now,
                            reason = publication.reason,
                        )
                )
            publications
                .stop(company, publication, now, job.failureCode ?: "job_failed", actor.accountId)
                .flatMap { amendments.save(company, restored, expectedVersion) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_amendment",
                                amendmentId,
                                "payroll.amendment_publication_recovered",
                                mapOf(
                                    "publicationId" to id.toString(),
                                    "jobId" to publication.jobId.toString(),
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
