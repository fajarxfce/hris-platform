package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.*
import java.util.UUID

interface PayrollRunRepository {
    fun find(company: UUID, id: UUID, lock: Boolean = false): Result<PayrollRun?>

    fun forJob(company: UUID, job: UUID, lock: Boolean = false): Result<PayrollRun?>

    fun count(company: UUID, period: UUID): Result<Int>

    fun list(company: UUID, period: UUID, after: Int?, limit: Int): Result<Page<PayrollRun>>

    fun create(
        company: UUID,
        run: PayrollRun,
        targets: List<PayrollRunTarget>,
        attempt: PayrollRunAttempt,
    ): Result<Unit>

    fun target(company: UUID, run: UUID, ordinal: Int): Result<PayrollRunTarget?>

    fun result(company: UUID, run: UUID, employee: UUID): Result<PayrollRunResult?>

    fun results(company: UUID, run: UUID, after: Int?, limit: Int): Result<Page<PayrollRunItem>>

    fun appendResult(company: UUID, run: PayrollRun, result: PayrollRunResult): Result<Unit>

    fun transition(
        company: UUID,
        run: PayrollRun,
        status: PayrollRunStatus,
        job: UUID = run.jobId,
    ): Result<MutationReceipt>

    fun finalize(company: UUID, run: PayrollRun, finalizationId: UUID): Result<MutationReceipt>

    fun attempts(company: UUID, run: UUID): Result<List<PayrollRunAttempt>>

    fun appendAttempt(company: UUID, run: UUID, attempt: PayrollRunAttempt): Result<Unit>
}
