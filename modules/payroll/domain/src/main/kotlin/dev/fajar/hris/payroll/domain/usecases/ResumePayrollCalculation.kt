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

class ResumePayrollCalculation(
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
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.calculate")
        if (access is Result.Failed) return access
        if (expectedVersion !in 0..24 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_run_action"))
        val key =
            OperationKey(
                "payroll.calculation_resume",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), reason),
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

            if (run != null && run.actorId != actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_run_author_required")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (run == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_run_not_found"))
            if (run.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (run.status !in setOf(PayrollRunStatus.PROCESSING, PayrollRunStatus.STOPPED))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_run_not_resumable"))
            val previous = jobs.find(company, run.jobId)
            if (previous is Result.Failed) return@run previous
            val job = (previous as Result.Success).value
            if (job == null || job.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_job_not_stopped"))
            val periodResult = periods.find(company, run.periodId)
            if (periodResult is Result.Failed) return@run periodResult
            val period = (periodResult as Result.Success).value
            if (period?.currentRunId != run.id || period.status != PayrollPeriodStatus.PROCESSING)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_run_job_obsolete"))
            val attempts = runs.attempts(company, id)
            if (attempts is Result.Failed) return@run attempts
            val number = (attempts as Result.Success).value.size + 1
            if (number > 8 || run.version >= 23)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_run_attempt_limit"))
            val queue = jobs.lockQueue(company)
            if (queue is Result.Failed) return@run queue
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val now = clock.instant()
            val next =
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
                    run.totalEmployees - run.processed + 1,
                )
            jobs
                .create(next)
                .flatMap {
                    runs.appendAttempt(
                        company,
                        id,
                        PayrollRunAttempt(next.id, number, run.processed, now, reason),
                    )
                }
                .flatMap { runs.transition(company, run, PayrollRunStatus.PROCESSING, next.id) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_run",
                                id,
                                "payroll.calculation_resumed",
                                mapOf(
                                    "jobId" to next.id.toString(),
                                    "attempt" to number.toString(),
                                ),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .map { receipt }
                }
        }
    }
}
