package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
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

class AdvancePayrollCalculation(
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
    private val compensation: CompensationRepository,
    private val inputs: PayrollInputRepository,
    private val openings: PayrollTaxOpeningRepository,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val scope = validatePayrollRunJob(actor, lease)
        if (scope is Result.Failed) return scope
        val request = lease.job.request
        val company = request.companyId
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
            val guard = policies.lock(company)
            if (guard is Result.Failed) return@run guard
            val found = runs.forJob(company, request.id, lock = true)
            if (found is Result.Failed) return@run found
            val run = (found as Result.Success).value
            if (run == null || run.status != PayrollRunStatus.PROCESSING)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_run_job_obsolete"))
            val attempts = runs.attempts(company, run.id)
            if (attempts is Result.Failed) return@run attempts
            val attempt =
                (attempts as Result.Success).value.singleOrNull { it.jobId == job.request.id }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_run_job_obsolete")
                    )
            val consistent = validatePayrollRunProgress(run, job, attempt)
            if (consistent is Result.Failed) return@run consistent

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
                (checked as Result.Success).value.requirePermission("payroll.calculate")
            if (permission is Result.Failed) return@run permission
            if (run.processed == run.totalEmployees) {
                val periodResult = periods.find(company, run.periodId)
                if (periodResult is Result.Failed) return@run periodResult
                val period = (periodResult as Result.Success).value
                if (
                    period == null ||
                        period.currentRunId != run.id ||
                        period.status != PayrollPeriodStatus.PROCESSING
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_run_job_obsolete")
                    )
                return@run jobs
                    .checkpoint(lease, JobProgress(request.totalItems, emptyMap()))
                    .flatMap { moved ->
                        if (!moved)
                            return@flatMap Result.Failed(
                                Failure(FailureKind.CONFLICT, "job_lease_lost")
                            )
                        runs
                            .transition(company, run, PayrollRunStatus.CALCULATED)
                            .flatMap {
                                periods.transition(
                                    company,
                                    period,
                                    PayrollPeriodStatus.CALCULATED,
                                    run.id,
                                    actor.accountId,
                                    clock.instant(),
                                    "Payroll calculation completed",
                                )
                            }
                            .flatMap { jobs.complete(lease, JobStatus.SUCCEEDED) }
                            .flatMap { finished ->
                                if (!finished)
                                    Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                                else
                                    journal.record(
                                        actor,
                                        ChangeRecord(
                                            "payroll_run",
                                            run.id,
                                            "payroll.calculation_completed",
                                            mapOf(
                                                "jobId" to request.id.toString(),
                                                "succeeded" to run.succeeded.toString(),
                                                "failed" to run.failed.toString(),
                                            ),
                                        ),
                                    )
                            }
                            .map { JobStep(request.totalItems, true) }
                    }
            }
            val next = runs.target(company, run.id, run.processed + 1)
            if (next is Result.Failed) return@run next
            val target =
                (next as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_target_missing")
                    )
            val version = people.currentVersion(company, target.employeeId)
            if (version is Result.Failed) return@run version
            val inputResult =
                target.inputRevision?.let {
                    inputs.revision(company, target.employeeId, run.earningsMonth, it)
                } ?: Result.Success(null)
            if (inputResult is Result.Failed) return@run inputResult
            val input = (inputResult as Result.Success).value
            val openingResult =
                target.taxOpeningRevision?.let {
                    openings.revision(company, target.employeeId, run.earningsMonth.year, it)
                } ?: Result.Success(null)
            if (openingResult is Result.Failed) return@run openingResult
            val opening = (openingResult as Result.Success).value
            val valid =
                requirePayrollSources(
                    target,
                    input,
                    opening,
                    run,
                    (version as Result.Success).value,
                )
            var facts: PayrollCalculationFacts? = null
            val outcome: Result<PayrollMonthlyCalculation> =
                if (valid is Result.Failed) valid
                else {
                    val reviewedInput = requireNotNull(input)
                    val reviewedOpening = requireNotNull(opening)
                    val policyResult = policies.revision(company, run.policyRevision)
                    if (policyResult is Result.Failed) return@run policyResult
                    val policy =
                        (policyResult as Result.Success).value
                            ?: return@run Result.Failed(
                                Failure(FailureKind.CONFLICT, "payroll_policy_snapshot_missing")
                            )
                    val termsResult =
                        compensation.revision(
                            company,
                            target.employeeId,
                            requireNotNull(target.compensationRevision),
                        )
                    if (termsResult is Result.Failed) return@run termsResult
                    val terms =
                        (termsResult as Result.Success).value
                            ?: return@run Result.Failed(
                                Failure(
                                    FailureKind.CONFLICT,
                                    "payroll_compensation_snapshot_missing",
                                )
                            )
                    val through =
                        maxOf(
                            run.earningsMonth.atEndOfMonth(),
                            reviewedInput.terms.holidayAllowance?.holidayDate
                                ?: run.earningsMonth.atEndOfMonth(),
                        )
                    val employment =
                        people.effectiveRevisions(
                            company,
                            target.employeeId,
                            run.earningsMonth.atDay(1),
                            through,
                        )
                    if (employment is Result.Failed) return@run employment
                    val work =
                        sources.workDays(
                            company,
                            run.workJobId,
                            target.employeeId,
                            run.earningsMonth,
                        )
                    if (work is Result.Failed) return@run work
                    val days = (work as Result.Success).value
                    val leave = sources.leaveDays(company, target.employeeId, run.earningsMonth)
                    if (leave is Result.Failed) return@run leave
                    if (days == null)
                        Result.Failed(
                            Failure(FailureKind.CONFLICT, "payroll_workforce_employee_missing")
                        )
                    else {
                        val snapshot =
                            PayrollCalculationFacts(
                                run.earningsMonth,
                                run.incomeDueDate,
                                run.plannedPaymentDate,
                                policy.copy(version = run.policyRevision),
                                terms.terms,
                                reviewedInput.terms,
                                (employment as Result.Success).value.map { it.terms },
                                days,
                                (leave as Result.Success).value,
                                reviewedOpening.terms,
                            )
                        facts = snapshot
                        calculateMonthlyPayroll(snapshot)
                    }
                }
            val value = (outcome as? Result.Success)?.value
            val failure = (outcome as? Result.Failed)?.failure
            if (
                failure != null &&
                    failure.kind !in
                        setOf(
                            FailureKind.VALIDATION,
                            FailureKind.NOT_FOUND,
                            FailureKind.CONFLICT,
                            FailureKind.FORBIDDEN,
                        )
            )
                return@run Result.Failed(failure)
            val item =
                PayrollRunItem(
                    target,
                    request.id,
                    clock.instant(),
                    failure,
                    value?.tax?.taxableGross,
                    value?.tax?.withheld,
                    value?.tax?.takeHome,
                )
            runs
                .appendResult(company, run, PayrollRunResult(item, facts, value))
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "payroll_run",
                            run.id,
                            "payroll.employee_calculated",
                            mapOf(
                                "employeeId" to target.employeeId.toString(),
                                "ordinal" to target.ordinal.toString(),
                                "status" to if (failure == null) "SUCCEEDED" else "FAILED",
                                "code" to (failure?.code ?: "payroll_employee_calculated"),
                            ),
                        ),
                    )
                }
                .flatMap { jobs.checkpoint(lease, JobProgress(job.completedItems + 1, emptyMap())) }
                .flatMap { moved ->
                    if (moved) Result.Success(JobStep(job.completedItems + 1, false))
                    else Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                }
        }
    }
}
