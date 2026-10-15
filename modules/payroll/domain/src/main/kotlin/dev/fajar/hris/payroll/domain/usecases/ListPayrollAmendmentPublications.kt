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

class ListPayrollAmendmentPublications(
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
) {
    fun execute(actor: Actor, amendmentId: UUID): Result<List<PayrollAmendmentPublicationDetails>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = requirePayrollAmendmentRead(actor)
        if (permission is Result.Failed) return permission
        return transactions.run(actor) {
            val payrollGuard = policies.lock(company, shared = true)
            if (payrollGuard is Result.Failed) return@run payrollGuard
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
            val acquiredAccess = identities.access(actor.accountId, company)
            if (acquiredAccess is Result.Failed) return@run acquiredAccess
            val access = (acquiredAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed = requirePayrollAmendmentRead(live)
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
            val foundPublications = publications.list(company, amendmentId)
            if (foundPublications is Result.Failed) return@run foundPublications
            val details = mutableListOf<PayrollAmendmentPublicationDetails>()
            for (publication in (foundPublications as Result.Success).value) {
                val foundReview = reviews.find(company, publication.reviewId)
                if (foundReview is Result.Failed) return@run foundReview
                val review =
                    (foundReview as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.UNEXPECTED, "payroll_amendment_review_missing")
                        )
                val foundApproval = approvals.find(company, review.approvalId)
                if (foundApproval is Result.Failed) return@run foundApproval
                val approval =
                    (foundApproval as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.UNEXPECTED, "payroll_amendment_review_missing")
                        )
                if (approval.requesterId == actor.accountId)
                    return@run Result.Failed(
                        Failure(
                            FailureKind.FORBIDDEN,
                            "payroll_amendment_self_preparation_forbidden",
                        )
                    )
                val foundJob = jobs.find(company, publication.jobId)
                if (foundJob is Result.Failed) return@run foundJob
                val job = (foundJob as Result.Success).value
                if (
                    job == null ||
                        job.request.kind != JobKind.PAYROLL_AMENDMENT_PUBLISH ||
                        job.request.actorId != publication.actorId ||
                        job.request.values["publicationId"] != publication.id.toString()
                )
                    return@run Result.Failed(
                        Failure(FailureKind.UNEXPECTED, "payroll_amendment_publication_job_missing")
                    )
                val actions =
                    if (
                        "payroll.amend.publish" in live.permissions &&
                            publication.status == PayrollAmendmentPublicationStatus.PENDING &&
                            current.summary.latestPublicationId == publication.id &&
                            current.summary.status == PayrollAmendmentStatus.PUBLISHING &&
                            job.status in setOf(JobStatus.FAILED, JobStatus.CANCELLED) &&
                            job.completedItems == 0
                    )
                        setOf(PayrollAmendmentPublicationAvailableAction.RECOVER)
                    else emptySet()
                details +=
                    PayrollAmendmentPublicationDetails(publication, job, current.summary, actions)
            }
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                java.util.List.copyOf(details)
            }
        }
    }
}
