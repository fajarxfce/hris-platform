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

class StartPayrollCalculation(
    private val runs: PayrollRunRepository,
    private val periods: PayrollPeriodRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val sources: PayrollCalculationSourceRepository,
    private val operations: OperationRepository,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        periodId: UUID,
        incomeDueDate: LocalDate,
        expectedPeriodVersion: Long,
        expectedWorkPeriodVersion: Long,
        expectedPolicyVersion: Long,
        reviewReference: String,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.calculate")
        if (access is Result.Failed) return access
        val valid =
            validatePayrollRunStart(
                incomeDueDate,
                reviewReference,
                reason,
                expectedPeriodVersion,
                expectedWorkPeriodVersion,
                expectedPolicyVersion,
            )
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "payroll.calculation_start",
                operationId,
                listOf(
                    id.toString(),
                    periodId.toString(),
                    incomeDueDate.toString(),
                    expectedPeriodVersion.toString(),
                    expectedWorkPeriodVersion.toString(),
                    expectedPolicyVersion.toString(),
                    reviewReference,
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = policies.lock(company)
            if (guard is Result.Failed) return@run guard
            val found = periods.find(company, periodId)
            if (found is Result.Failed) return@run found
            val period = (found as Result.Success).value
            val source =
                period?.let { sources.workPeriod(company, it.earningsMonth, lock = true) }
                    ?: Result.Success(null)
            if (source is Result.Failed) return@run source

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

            val permitted =
                requirePayrollMutation(
                    (checked as Result.Success).value,
                    "payroll.calculate",
                    clock.instant(),
                    security,
                )
            if (permitted is Result.Failed) return@run permitted
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (period == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_period_not_found"))
            if (period.version != expectedPeriodVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (period.status != PayrollPeriodStatus.DRAFT || period.currentRunId != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_period_not_draft"))
            if (period.version > 250)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_period_revision_limit")
                )
            if (YearMonth.from(incomeDueDate) != period.earningsMonth)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_tax_month_review_required")
                )
            val work = (source as Result.Success).value
            if (work == null || !work.closed || work.jobId == null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_workforce_not_closed")
                )
            if (work.version != expectedWorkPeriodVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_workforce_version"))
            if (work.timezone != period.timezone)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_timezone_review_required")
                )
            val policyResult =
                policies.effective(company, period.earningsMonth).flatMap {
                    requireEffectivePayrollPolicy(it, period.earningsMonth)
                }
            if (policyResult is Result.Failed) return@run policyResult
            val policy = (policyResult as Result.Success).value
            if (policy.version != expectedPolicyVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_policy_version"))
            val count = runs.count(company, periodId)
            if (count is Result.Failed) return@run count
            val number = (count as Result.Success).value + 1
            if (number > 20)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_run_limit"))
            val targetsResult = sources.targets(company, periodId, period.earningsMonth)
            if (targetsResult is Result.Failed) return@run targetsResult
            val targets = (targetsResult as Result.Success).value
            if (targets.size != period.participantCount)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_participant_snapshot_incomplete")
                )
            val pending = sources.pendingLeave(company, periodId, period.earningsMonth)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_leave_decision_pending")
                )
            val queue = jobs.lockQueue(company)
            if (queue is Result.Failed) return@run queue
            val pendingJobs = jobs.pendingCount(company)
            if (pendingJobs is Result.Failed) return@run pendingJobs
            if ((pendingJobs as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val now = clock.instant()
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.PAYROLL_CALCULATE,
                    operationId,
                    mapOf("runId" to id.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    targets.size + 1,
                )
            val run =
                PayrollRun(
                    id,
                    periodId,
                    number,
                    period.earningsMonth,
                    incomeDueDate,
                    period.plannedPaymentDate,
                    period.timezone,
                    work.jobId,
                    work.version,
                    policy.appliedRevision,
                    targets.size,
                    actor.accountId,
                    reviewReference,
                    reason,
                    now,
                    job.id,
                    PayrollRunStatus.PROCESSING,
                    0,
                )
            val receipt = MutationReceipt(id, 0)
            jobs
                .create(job)
                .flatMap {
                    runs.create(company, run, targets, PayrollRunAttempt(job.id, 1, 0, now, reason))
                }
                .flatMap {
                    periods.transition(
                        company,
                        period,
                        PayrollPeriodStatus.PROCESSING,
                        id,
                        actor.accountId,
                        now,
                        reason,
                    )
                }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "payroll_run",
                            id,
                            "payroll.calculation_started",
                            mapOf(
                                "periodId" to periodId.toString(),
                                "earningsMonth" to period.earningsMonth.toString(),
                                "employees" to targets.size.toString(),
                                "jobId" to job.id.toString(),
                            ),
                            reason,
                        ),
                    )
                }
                .flatMap { operations.record(actor, key, receipt) }
                .map { receipt }
        }
    }
}
