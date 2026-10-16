package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.records.AttendanceAdjustmentsRecord
import dev.fajar.hris.workforce.data.models.AttendanceAdjustmentSummaryRow
import java.util.UUID

interface AttendanceAdjustmentDataSource {
    fun latest(companyId: UUID, jobId: UUID, employeeId: UUID): AttendanceAdjustmentsRecord?

    fun find(companyId: UUID, id: UUID): AttendanceAdjustmentsRecord?

    fun history(
        companyId: UUID,
        jobId: UUID,
        employeeId: UUID,
        before: Long?,
        limit: Int,
    ): List<AttendanceAdjustmentSummaryRow>

    fun append(row: AttendanceAdjustmentsRecord)
}
