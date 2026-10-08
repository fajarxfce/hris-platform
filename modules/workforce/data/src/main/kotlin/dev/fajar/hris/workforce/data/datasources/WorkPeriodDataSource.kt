package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface WorkPeriodDataSource {
    fun ensure(companyId: UUID, month: LocalDate)

    fun find(companyId: UUID, month: LocalDate, lock: String?): WorkPeriodsRecord?

    fun forJob(companyId: UUID, jobId: UUID, lock: Boolean): WorkPeriodsRecord?

    fun range(
        companyId: UUID,
        from: LocalDate,
        until: LocalDate?,
        lock: Boolean,
    ): List<WorkPeriodsRecord>

    fun update(row: WorkPeriodsRecord, expectedVersion: Long): WorkPeriodsRecord?

    fun insertTargets(rows: List<WorkPeriodTargetsRecord>)

    fun nextTarget(companyId: UUID, jobId: UUID, after: UUID?): UUID?

    fun snapshot(companyId: UUID, jobId: UUID, employeeId: UUID): WorkPeriodSnapshotsRecord?

    fun insertSnapshot(row: WorkPeriodSnapshotsRecord)

    fun countSnapshots(companyId: UUID, jobId: UUID): Int
}
