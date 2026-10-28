package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveReport(
    val companyId: UUID,
    val from: LocalDate,
    val until: LocalDate,
    val evaluatedAt: Instant,
    val aggregate: LeaveReportAggregate,
) {
    val definitionVersion = "leave.v1"
}
