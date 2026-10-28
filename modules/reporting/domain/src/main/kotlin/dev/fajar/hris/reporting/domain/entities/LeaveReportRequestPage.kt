package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.leave.domain.entities.LeaveStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveReportRequestPage(
    val companyId: UUID,
    val from: LocalDate,
    val until: LocalDate,
    val status: LeaveStatus?,
    val evaluatedAt: Instant,
    val page: Page<LeaveReportRequest>,
) {
    val definitionVersion = "leave.v1"
}
