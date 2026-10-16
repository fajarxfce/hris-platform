package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.AttendanceAdjustmentsRecord
import java.time.LocalDate
import java.util.UUID

data class PayrollAmendmentAttendanceRow(
    val companyId: UUID,
    val assessmentId: UUID,
    val employeeId: UUID,
    val workJobId: UUID,
    val month: LocalDate,
    val adjustment: AttendanceAdjustmentsRecord,
    val latestAdjustmentId: UUID,
)
