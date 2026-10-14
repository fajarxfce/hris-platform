package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetPayrollReview(
    private val reviews: PayrollReviewRepository,
    private val policies: PayrollPolicyRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val runs: PayrollRunRepository,
    private val finalizations: PayrollFinalizationRepository,
    private val jobs: JobRepository,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<PayrollReviewDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollInput(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        return transactions.run(actor) {
            val guard = policies.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard
            val approvalGuard = approvals.lock(company, shared = true)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val found = reviews.find(company, id)
            if (found is Result.Failed) return@run found
            val review = (found as Result.Success).value
            val foundApproval =
                review?.let { approvals.find(company, it.approvalId) } ?: Result.Success(null)
            if (foundApproval is Result.Failed) return@run foundApproval
            val approval = (foundApproval as Result.Success).value
            val delegated = approvals.delegations(company, actor.accountId, clock.instant())
            if (delegated is Result.Failed) return@run delegated
            val delegations = (delegated as Result.Success).value
            if (delegations.size > 200)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "approval_delegation_capacity")
                )
            val assigned = approval?.stages?.flatMap { it.assignees }?.toSet() ?: emptySet()
            val decidingParties =
                delegations
                    .filter { it.kind == ApprovalKind.PAYROLL && it.fromAccount in assigned }
                    .map { it.fromAccount }
                    .toSet() + actor.accountId
            for (party in decidingParties.sorted()) {
                val accountGuard = identities.lockAccount(party, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (!canReadPayrollInput(live))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            if (review == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_review_not_found"))
            if (approval == null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_unavailable"))
            val foundRun = runs.find(company, review.runId)
            if (foundRun is Result.Failed) return@run foundRun
            val run =
                (foundRun as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_run_not_found")
                    )
            val history = reviews.changes(company, id)
            if (history is Result.Failed) return@run history
            val changes = (history as Result.Success).value
            val displayParties =
                assigned +
                    changes.flatMap { listOfNotNull(it.actorId, it.decidingFor) } +
                    run.actorId +
                    review.submittedBy
            val parties = displayParties + decidingParties
            if (parties.size > 2000)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_group_too_large"))
            val candidates = members.candidates(company, parties, emptySet(), parties.size)
            if (candidates is Result.Failed) return@run candidates
            val accounts = (candidates as Result.Success).value
            val finalizationResult = finalizations.latest(company, run.id)
            if (finalizationResult is Result.Failed) return@run finalizationResult
            val finalization = (finalizationResult as Result.Success).value
            val finalizationJob =
                finalization?.let { jobs.find(company, it.jobId) } ?: Result.Success(null)
            if (finalizationJob is Result.Failed) return@run finalizationJob
            val finalizationActive =
                (finalizationJob as Result.Success).value?.status in
                    setOf(JobStatus.QUEUED, JobStatus.RUNNING)
            val now = clock.instant()
            validateCompanySessionActor(actor, access, now, security).map {
                PayrollReviewDetails(
                    review,
                    approval,
                    changes,
                    run,
                    accounts
                        .filter { it.id in displayParties }
                        .map { PayrollReviewAccount(it.id, it.displayName) },
                    finalizationActive,
                    availablePayrollReviewActions(
                        live,
                        review,
                        run,
                        approval,
                        delegations,
                        accounts,
                        finalizationActive,
                        now,
                    ),
                )
            }
        }
    }
}
