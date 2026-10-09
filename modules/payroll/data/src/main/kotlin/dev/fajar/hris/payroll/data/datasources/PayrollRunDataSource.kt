package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID

interface PayrollRunDataSource {
    fun find(company: UUID, id: UUID, lock: Boolean): PayrollRunsRecord?

    fun forJob(company: UUID, job: UUID, lock: Boolean): PayrollRunsRecord?

    fun count(company: UUID, period: UUID): Int

    fun list(company: UUID, period: UUID, after: Int?, limit: Int): List<PayrollRunsRecord>

    fun insert(row: PayrollRunsRecord)

    fun insertTargets(rows: List<PayrollRunTargetsRecord>)

    fun target(company: UUID, run: UUID, ordinal: Int): PayrollRunTargetsRecord?

    fun result(company: UUID, run: UUID, employee: UUID): PayrollRunResultRow?

    fun results(company: UUID, run: UUID, after: Int?, limit: Int): List<PayrollRunItemRow>

    fun insertResult(row: PayrollRunResultsRecord)

    fun advanceProgress(company: UUID, run: UUID, processed: Int, success: Boolean): Boolean

    fun transition(company: UUID, run: UUID, version: Long, status: String, job: UUID): Long?

    fun finalize(company: UUID, run: UUID, version: Long, finalization: UUID): Long?

    fun attempts(company: UUID, run: UUID): List<PayrollRunAttemptsRecord>

    fun insertAttempt(row: PayrollRunAttemptsRecord)
}
