package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class AttendanceClosingEmployeePageResponse(
    val companyId: UUID,
    val month: YearMonth,
    val evaluatedAt: Instant,
    val definitionVersion: String,
    val items: List<AttendanceClosingEmployeeResponse>,
    val nextCursor: String?,
)
