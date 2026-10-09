package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.*
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredPayrollRunRepository(
    private val source: PayrollRunDataSource,
    private val json: ObjectMapper,
) : PayrollRunRepository {
    override fun find(company: UUID, id: UUID, lock: Boolean): Result<PayrollRun?> =
        safeDatabaseCall {
            source.find(company, id, lock)?.toRun()
        }

    override fun forJob(company: UUID, job: UUID, lock: Boolean): Result<PayrollRun?> =
        safeDatabaseCall {
            source.forJob(company, job, lock)?.toRun()
        }

    override fun count(company: UUID, period: UUID): Result<Int> = safeDatabaseCall {
        source.count(company, period)
    }

    override fun list(
        company: UUID,
        period: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<PayrollRun>> = safeDatabaseCall {
        val rows = source.list(company, period, after, limit + 1)
        Page(
            rows.take(limit).map { it.toRun() },
            if (rows.size > limit) rows[limit - 1].runNumber.toString() else null,
        )
    }

    override fun create(
        company: UUID,
        run: PayrollRun,
        targets: List<PayrollRunTarget>,
        attempt: PayrollRunAttempt,
    ): Result<Unit> = safeDatabaseCall {
        source.insert(run.toRecord(company))
        source.insertTargets(targets.map { it.toRecord(company, run) })
        source.insertAttempt(attempt.toRecord(company, run.id))
    }

    override fun target(company: UUID, run: UUID, ordinal: Int): Result<PayrollRunTarget?> =
        safeDatabaseCall {
            source.target(company, run, ordinal)?.toTarget()
        }

    override fun result(company: UUID, run: UUID, employee: UUID): Result<PayrollRunResult?> =
        safeDatabaseCall {
            source.result(company, run, employee)?.toResult(json)
        }

    override fun results(
        company: UUID,
        run: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<PayrollRunItem>> = safeDatabaseCall {
        val rows = source.results(company, run, after, limit + 1)
        Page(
            rows.take(limit).map { it.toItem(json) },
            if (rows.size > limit) rows[limit - 1].target.ordinal.toString() else null,
        )
    }

    override fun appendResult(
        company: UUID,
        run: PayrollRun,
        result: PayrollRunResult,
    ): Result<Unit> =
        safeDatabaseCall {
                source.insertResult(result.toRecord(company, run.id, json))
                source.advanceProgress(company, run.id, run.processed, result.item.failure == null)
            }
            .flatMap {
                if (it) Result.Success(Unit)
                else
                    Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_run_checkpoint_inconsistent")
                    )
            }

    override fun transition(
        company: UUID,
        run: PayrollRun,
        status: PayrollRunStatus,
        job: UUID,
    ): Result<MutationReceipt> =
        safeDatabaseCall { source.transition(company, run.id, run.version, status.name, job) }
            .requireCurrentVersion()
            .map { MutationReceipt(run.id, it) }

    override fun finalize(
        company: UUID,
        run: PayrollRun,
        finalizationId: UUID,
    ): Result<MutationReceipt> =
        safeDatabaseCall { source.finalize(company, run.id, run.version, finalizationId) }
            .requireCurrentVersion()
            .map { MutationReceipt(run.id, it) }

    override fun attempts(company: UUID, run: UUID): Result<List<PayrollRunAttempt>> =
        safeDatabaseCall {
            source.attempts(company, run).map { it.toAttempt() }
        }

    override fun appendAttempt(company: UUID, run: UUID, attempt: PayrollRunAttempt): Result<Unit> =
        safeDatabaseCall {
            source.insertAttempt(attempt.toRecord(company, run))
        }
}
