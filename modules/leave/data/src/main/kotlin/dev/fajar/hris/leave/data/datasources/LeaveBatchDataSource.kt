package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface LeaveBatchDataSource {
    fun lock(company: UUID)

    fun active(company: UUID, type: UUID, kind: String, period: LocalDate): Boolean

    fun find(company: UUID, id: UUID, lock: Boolean): LeaveBatchesRecord?

    fun forJob(company: UUID, jobId: UUID, lock: Boolean): LeaveBatchesRecord?

    fun list(company: UUID, after: UUID?, limit: Int): List<LeaveBatchesRecord>

    fun insert(row: LeaveBatchesRecord)

    fun insertTargets(rows: List<LeaveBatchTargetsRecord>)

    fun insertAttempt(row: LeaveBatchAttemptsRecord)

    fun next(company: UUID, batch: UUID): LeaveBatchTargetsRecord?

    fun outcome(row: LeaveBatchResultsRecord)

    fun counts(company: UUID, batch: UUID): Map<String, Int>

    fun results(company: UUID, batch: UUID, after: Int?, limit: Int): List<LeaveBatchResultsRecord>

    fun attempts(company: UUID, batch: UUID): List<LeaveBatchAttemptsRecord>

    fun transition(company: UUID, batch: UUID, version: Long, status: String, jobId: UUID): Long?
}
