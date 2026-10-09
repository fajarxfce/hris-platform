package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.requireRecentAuthentication
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*

class AdvancePayrollFinalization(
    private val finalizations: PayrollFinalizationRepository,
    private val runs: PayrollRunRepository,
    private val reviews: PayrollReviewRepository,
    private val policies: PayrollPolicyRepository,
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
    private val periods: PayrollPeriodRepository,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val request = lease.job.request
        val company = request.companyId
        if (
            actor.companyId != company ||
                actor.accountId != request.actorId ||
                actor.credentialVersion != request.credentialVersion ||
                request.kind != JobKind.PAYROLL_FINALIZE
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        val access = actor.requirePermission("payroll.finalize")
        if (access is Result.Failed) return access
        return transactions.run(actor) {
            val leased = jobs.lockLease(lease)
            if (leased is Result.Failed) return@run leased
            val job =
                (leased as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            if (job.cancellationRequested)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested")
                )
            if (job.completedItems != 0 || request.totalItems != 1)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_finalization_obsolete")
                )
            val guard = policies.lock(company)
            if (guard is Result.Failed) return@run guard
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
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("payroll.finalize")
            if (permission is Result.Failed) return@run permission
            // The accepted command proved MFA; its credential version is fenced again here.
            val recent =
                requireRecentAuthentication(live, clock.instant(), security.recentAuthenticationAge)
            if (recent is Result.Failed) return@run recent
            val found = finalizations.forJob(company, request.id)
            if (found is Result.Failed) return@run found
            val finalization = (found as Result.Success).value
            if (
                finalization == null ||
                    finalization.publishedAt != null ||
                    finalization.actorId != actor.accountId
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_finalization_obsolete")
                )
            val runResult = runs.find(company, finalization.runId)
            if (runResult is Result.Failed) return@run runResult
            val run =
                (runResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_finalization_obsolete")
                    )
            val reviewResult = reviews.find(company, finalization.reviewId)
            if (reviewResult is Result.Failed) return@run reviewResult
            val review =
                (reviewResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_approved_review_required")
                    )
            val approvalResult = approvals.find(company, review.approvalId)
            if (approvalResult is Result.Failed) return@run approvalResult
            val approval = (approvalResult as Result.Success).value
            val approved = requireApprovedPayroll(run, review, approval)
            if (approved is Result.Failed) return@run approved
            if (
                run.version != finalization.runVersion ||
                    review.version != finalization.reviewVersion ||
                    approval!!.version != finalization.approvalVersion
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_finalization_obsolete")
                )
            val source =
                finalizations.readiness(company, run.id).flatMap(::requirePayrollPublicationSources)
            if (source is Result.Failed) return@run source
            val periodResult = periods.find(company, run.periodId)
            if (periodResult is Result.Failed) return@run periodResult
            val period = (periodResult as Result.Success).value
            if (
                period == null ||
                    period.currentRunId != run.id ||
                    period.status != PayrollPeriodStatus.CALCULATED
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_finalization_obsolete")
                )
            val now = clock.instant()
            finalizations
                .publish(company, finalization, now)
                .flatMap { runs.finalize(company, run, finalization.id) }
                .flatMap {
                    periods.transition(
                        company,
                        period,
                        PayrollPeriodStatus.FINALIZED,
                        run.id,
                        actor.accountId,
                        now,
                        finalization.reason,
                    )
                }
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
                                "payroll_finalization",
                                finalization.id,
                                "payroll.finalized",
                                mapOf(
                                    "runId" to run.id.toString(),
                                    "employees" to run.totalEmployees.toString(),
                                    "jobId" to request.id.toString(),
                                ),
                                finalization.reason,
                            ),
                        )
                }
                .map { JobStep(1, true) }
        }
    }
}
