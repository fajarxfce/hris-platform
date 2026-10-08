package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveOccupancyRow
import dev.fajar.hris.schema.tables.records.LeaveAllocationsRecord
import java.time.LocalDate
import java.util.UUID

interface LeaveAllocationDataSource {
    fun occupancy(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<LeaveOccupancyRow>

    fun insert(rows: List<LeaveAllocationsRecord>)

    fun remove(company: UUID, id: UUID): Int
}
