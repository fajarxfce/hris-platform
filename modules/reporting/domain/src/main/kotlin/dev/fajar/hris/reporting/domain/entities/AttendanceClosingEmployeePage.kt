package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class AttendanceClosingEmployeePage(
    val companyId: UUID,
    val month: YearMonth,
    val evaluatedAt: Instant,
    val page: Page<AttendanceClosingEmployee>,
) {
    val definitionVersion = "attendance-closings.v1"
}
