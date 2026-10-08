package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface HolidayDataSource {
    fun find(companyId: UUID, id: UUID): WorkHolidaysRecord?

    fun list(companyId: UUID, from: LocalDate, until: LocalDate): List<WorkHolidaysRecord>

    fun insert(row: WorkHolidaysRecord)

    fun update(row: WorkHolidaysRecord, expectedVersion: Long): Long?

    fun append(row: HolidayRevisionsRecord)
}
