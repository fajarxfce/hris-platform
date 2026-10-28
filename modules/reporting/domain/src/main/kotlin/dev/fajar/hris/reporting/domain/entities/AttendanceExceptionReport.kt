package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceExceptionReport(
    val companyId: UUID,
    val from: LocalDate,
    val until: LocalDate,
    val evaluatedAt: Instant,
    val aggregate: AttendanceExceptionAggregate,
) {
    val definitionVersion = "attendance-exceptions.v1"
}
