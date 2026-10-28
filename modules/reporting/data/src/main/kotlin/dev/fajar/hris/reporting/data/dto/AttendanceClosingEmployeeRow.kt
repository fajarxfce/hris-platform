package dev.fajar.hris.reporting.data.dto

import java.time.OffsetDateTime
import java.util.UUID

data class AttendanceClosingEmployeeRow(
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val adjustmentId: UUID?,
    val adjustmentRevision: Long,
    val adjustmentAt: OffsetDateTime?,
    val report: AttendanceClosingReportRow,
)
