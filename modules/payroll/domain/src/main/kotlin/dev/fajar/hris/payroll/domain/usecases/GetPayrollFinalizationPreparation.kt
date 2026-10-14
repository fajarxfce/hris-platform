package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class GetPayrollFinalizationPreparation(
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
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, runId: UUID): Result<PayrollFinalizationPreparation> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollFinalization(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        return transactions.run(actor) {
            val guard = policies.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard
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
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (!canReadPayrollFinalization(live))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val found = runs.find(company, runId)
            if (found is Result.Failed) return@run found
            val run =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_run_not_found")
                    )
            val reviewResult = reviews.latest(company, runId)
            if (reviewResult is Result.Failed) return@run reviewResult
            val review = (reviewResult as Result.Success).value
            val approvalResult = review?.let { approvals.find(company, it.approvalId) }
            if (approvalResult is Result.Failed) return@run approvalResult
            val approval = (approvalResult as? Result.Success)?.value
            val totalResult = reviews.totals(company, runId)
            if (totalResult is Result.Failed) return@run totalResult
            val latest = finalizations.latest(company, runId)
            if (latest is Result.Failed) return@run latest
            val previous = (latest as Result.Success).value
            val jobResult = previous?.let { jobs.find(company, it.jobId) }
            if (jobResult is Result.Failed) return@run jobResult
            val job = (jobResult as? Result.Success)?.value
            // Status is advisory. Do not acquire a job lock after the payroll guard.
            val eligibility =
                requireApprovedPayroll(run, review, approval).flatMap {
                    nextPayrollFinalizationAttempt(previous, job?.status)
                }
            val ready =
                if (eligibility is Result.Failed) eligibility
                else {
                    val acquired = finalizations.readiness(company, runId)
                    if (acquired is Result.Failed) return@run acquired
                    requirePayrollPublicationSources((acquired as Result.Success).value)
                }
            val issues = if (ready is Result.Failed) listOf(ready.failure) else emptyList()
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                PayrollFinalizationPreparation(
                    run,
                    (totalResult as Result.Success).value,
                    review,
                    approval?.version,
                    previous,
                    job?.status,
                    issues,
                    if ("payroll.finalize" in live.permissions && issues.isEmpty())
                        setOf(PayrollFinalizationAvailableAction.START)
                    else emptySet(),
                )
            }
        }
    }
}
