package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
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
import java.util.UUID

class AbandonPayrollCalculation(
    private val runs: PayrollRunRepository,
    private val periods: PayrollPeriodRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val reviews: PayrollReviewRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        expectedPeriodVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.calculate")
        if (access is Result.Failed) return access
        if (expectedVersion !in 0..24 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_run_action"))
        if (expectedPeriodVersion !in 0..254)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "payroll.calculation_abandon",
                operationId,
                listOf(
                    id.toString(),
                    expectedVersion.toString(),
                    expectedPeriodVersion.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = policies.lock(company)
            if (guard is Result.Failed) return@run guard
            val found = runs.find(company, id, lock = true)
            if (found is Result.Failed) return@run found
            val run = (found as Result.Success).value
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock

            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val permission =
                requirePayrollMutation(
                    (checked as Result.Success).value,
                    "payroll.calculate",
                    clock.instant(),
                    security,
                )
            if (permission is Result.Failed) return@run permission

            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (run == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_run_not_found"))
            if (run.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (run.status == PayrollRunStatus.ABANDONED)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_run_already_abandoned")
                )
            val reviewResult = reviews.latest(company, run.id)
            if (reviewResult is Result.Failed) return@run reviewResult
            if (
                (reviewResult as Result.Success).value?.status in
                    setOf(PayrollReviewStatus.PENDING, PayrollReviewStatus.APPROVED)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_review_withdrawal_required")
                )
            val previous = jobs.find(company, run.jobId)
            if (previous is Result.Failed) return@run previous
            val job = (previous as Result.Success).value
            if (job == null || job.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_job_not_stopped"))
            val periodResult = periods.find(company, run.periodId)
            if (periodResult is Result.Failed) return@run periodResult
            val period = (periodResult as Result.Success).value
            if (period?.currentRunId != run.id)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_run_job_obsolete"))
            if (period.version != expectedPeriodVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            runs.transition(company, run, PayrollRunStatus.ABANDONED).flatMap { receipt ->
                periods
                    .transition(
                        company,
                        period,
                        PayrollPeriodStatus.DRAFT,
                        null,
                        actor.accountId,
                        clock.instant(),
                        reason,
                    )
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "payroll_run",
                                id,
                                "payroll.calculation_abandoned",
                                mapOf("periodId" to run.periodId.toString()),
                                reason,
                            ),
                        )
                    }
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}
