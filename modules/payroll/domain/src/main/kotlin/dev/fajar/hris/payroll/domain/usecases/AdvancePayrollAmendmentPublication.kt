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

class AdvancePayrollAmendmentPublication(
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
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val request = lease.job.request
        val company = request.companyId
        if (
            actor.companyId != company ||
                actor.accountId != request.actorId ||
                actor.credentialVersion != request.credentialVersion ||
                actor.authenticatedAt != request.authenticatedAt ||
                request.kind != JobKind.PAYROLL_AMENDMENT_PUBLISH
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        return transactions.run(actor) {
            val leased = jobs.lockLease(lease)
            if (leased is Result.Failed) return@run leased
            val job =
                (leased as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            if (job.request != request)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
            if (job.cancellationRequested)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested")
                )
            if (job.completedItems != 0 || request.totalItems != 1)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_publication_obsolete")
                )
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
            val checked = validateCompanyCommandActor(actor, access)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed =
                live.requirePermission("payroll.amend.publish").flatMap {
                    requireRecentAuthentication(
                        live,
                        clock.instant(),
                        security.recentAuthenticationAge,
                    )
                }
            if (allowed is Result.Failed) return@run allowed
            val foundPublication = publications.forJob(company, request.id)
            if (foundPublication is Result.Failed) return@run foundPublication
            val publication = (foundPublication as Result.Success).value
            if (
                publication == null ||
                    publication.status != PayrollAmendmentPublicationStatus.PENDING ||
                    publication.actorId != actor.accountId ||
                    request.values["publicationId"] != publication.id.toString() ||
                    request.values["amendmentId"] != publication.amendmentId.toString()
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_publication_obsolete")
                )
            val amendmentId = publication.amendmentId
            val reviewId = publication.reviewId
            val calculationId = publication.calculationId
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
            if (
                current.summary.version != publication.amendmentVersion + 1 ||
                    current.summary.status != PayrollAmendmentStatus.PUBLISHING ||
                    current.summary.latestPublicationId != publication.id ||
                    review.version != publication.reviewVersion ||
                    approval.version != publication.approvalVersion
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_publication_obsolete")
                )
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
            val acquiredAttendance =
                sources.attendance(
                    company,
                    payrollAmendmentAttendanceReferences(current.terms.corrections),
                )
            if (acquiredAttendance is Result.Failed) return@run acquiredAttendance
            val fresh =
                requirePayrollAmendmentPublicationSources(
                    current,
                    preview,
                    (acquiredSources as Result.Success).value,
                    (acquiredAttendance as Result.Success).value,
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
            val now = clock.instant()
            val published =
                current.copy(
                    summary =
                        current.summary.copy(
                            version = current.summary.version + 1,
                            status = PayrollAmendmentStatus.PUBLISHED,
                            action = PayrollAmendmentAction.PUBLISHED,
                            actorId = actor.accountId,
                            recordedAt = now,
                            reason = publication.reason,
                        )
                )
            publications
                .publish(company, publication, now)
                .flatMap { amendments.save(company, published, current.summary.version) }
                .flatMap { jobs.checkpoint(lease, JobProgress(1, emptyMap())) }
                .flatMap { moved ->
                    if (!moved) Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                    else jobs.complete(lease, JobStatus.SUCCEEDED)
                }
                .flatMap { finished ->
                    if (!finished) Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                    else
                        journal.record(
                            actor,
                            ChangeRecord(
                                "payroll_amendment",
                                amendmentId,
                                "payroll.amendment_published",
                                mapOf(
                                    "publicationId" to publication.id.toString(),
                                    "jobId" to request.id.toString(),
                                    "assessmentCount" to preview.calculations.size.toString(),
                                ),
                                publication.reason,
                            ),
                        )
                }
                .flatMap { validateCompanyCommandActor(actor, access) }
                .flatMap {
                    requireRecentAuthentication(
                        it,
                        clock.instant(),
                        security.recentAuthenticationAge,
                    )
                }
                .map { JobStep(1, true) }
        }
    }
}
