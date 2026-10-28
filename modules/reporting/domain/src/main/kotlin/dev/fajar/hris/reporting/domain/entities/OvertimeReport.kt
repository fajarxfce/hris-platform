package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class OvertimeReport(
    val companyId: UUID,
    val from: LocalDate,
    val until: LocalDate,
    val status: OvertimeStatus?,
    val evaluatedAt: Instant,
    val aggregate: OvertimeReportAggregate,
) {
    val definitionVersion = "overtime.v1"
}
