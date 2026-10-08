package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class StartWorkPeriodClose(
    private val periods: WorkPeriodRepository,
    private val jobs: JobRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val schedules: ScheduleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        month: YearMonth,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("workforce.close")
        if (access is Result.Failed) return access
        if (month.year !in 2000..2100 || version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_work_period_close"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "workforce.period_close",
                operationId,
                listOf(month.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val settingsResult = companies.find(company)
            if (settingsResult is Result.Failed) return@run settingsResult
            val settings =
                (settingsResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            if (month >= YearMonth.from(now.atZone(ZoneId.of(settings.timezone))))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "work_period_not_ended"))
            val scheduleLock = schedules.lock(company)
            if (scheduleLock is Result.Failed) return@run scheduleLock
            val found = periods.lockMonth(company, month, true)
            if (found is Result.Failed) return@run found
            val period = (found as Result.Success).value
            if (period.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val mutable = requireMutablePeriod(period)
            if (mutable is Result.Failed) return@run mutable
            val queueLock = jobs.lockQueue(company)
            if (queueLock is Result.Failed) return@run queueLock
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val roster = people.employeeIds(company, 5001)
            if (roster is Result.Failed) return@run roster
            val ids = (roster as Result.Success).value
            if (ids.size > 5000)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "work_period_employee_limit")
                )
            val request =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.WORKFORCE_CLOSE,
                    operationId,
                    mapOf("month" to month.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    ids.size + 1,
                )
            jobs
                .create(request)
                .flatMap { periods.start(company, period, request.id, settings.timezone, now, ids) }
                .flatMap { started ->
                    val receipt = MutationReceipt(request.id, started.version)
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "work_period",
                                period.id,
                                "workforce.period_close_started",
                                mapOf(
                                    "month" to month.toString(),
                                    "jobId" to request.id.toString(),
                                    "employees" to ids.size.toString(),
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
