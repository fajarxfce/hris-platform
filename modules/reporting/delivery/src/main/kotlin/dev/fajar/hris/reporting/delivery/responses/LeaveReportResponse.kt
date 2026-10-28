package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveReportResponse(
    val companyId: UUID,
    val from: LocalDate,
    val until: LocalDate,
    val evaluatedAt: Instant,
    val definitionVersion: String,
    val totals: LeaveReportCountsResponse,
    val statuses: List<LeaveReportStatusResponse>,
)
