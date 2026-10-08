package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class RecoverWorkPeriod(
    private val periods: WorkPeriodRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
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
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_work_period_recovery"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "workforce.period_recover",
                operationId,
                listOf(month.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = periods.find(company, month)
            if (found is Result.Failed) return@run found
            val initial = (found as Result.Success).value
            val jobId =
                initial?.jobId
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "work_period_recovery_unavailable")
                    )
            val lockedJob = jobs.find(company, jobId, true)
            if (lockedJob is Result.Failed) return@run lockedJob
            val job = (lockedJob as Result.Success).value
            val locked = periods.forJob(company, jobId, true)
            if (locked is Result.Failed) return@run locked
            val period = (locked as Result.Success).value
            if (period == null || period.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                period.status != WorkPeriodStatus.PROCESSING ||
                    job?.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "work_period_recovery_unavailable")
                )
            periods.requireReview(company, period, job?.failureCode ?: "job_failed").flatMap {
                changed ->
                val receipt = MutationReceipt(changed.id, changed.version)
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "work_period",
                            period.id,
                            "workforce.period_recovered",
                            mapOf("jobId" to jobId.toString()),
                            reason,
                        ),
                    )
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}
