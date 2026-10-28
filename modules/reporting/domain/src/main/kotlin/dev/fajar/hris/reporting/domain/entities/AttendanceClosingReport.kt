package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class AttendanceClosingReport(
    val companyId: UUID,
    val from: YearMonth,
    val until: YearMonth,
    val evaluatedAt: Instant,
    val aggregate: AttendanceClosingReportAggregate,
    val definitionVersion: String = ReportKind.ATTENDANCE_CLOSINGS.definitionVersion,
)
