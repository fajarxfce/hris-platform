package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.records.AttendanceCorrectionsRecord
import java.time.LocalDate
import java.util.UUID

interface AttendanceCorrectionDataSource {
    fun latest(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<AttendanceCorrectionsRecord>

    fun history(
        company: UUID,
        employee: UUID,
        date: LocalDate,
        after: Long?,
        limit: Int,
    ): List<AttendanceCorrectionsRecord>

    fun insertVersion(company: UUID, employee: UUID, date: LocalDate)

    fun advanceVersion(company: UUID, employee: UUID, date: LocalDate, expectedVersion: Long): Long?

    fun append(row: AttendanceCorrectionsRecord)
}
