package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class HeadcountReport(
    val companyId: UUID,
    val asOf: LocalDate,
    val evaluatedAt: Instant,
    val counts: HeadcountCounts,
) {
    val definitionVersion: String = "headcount.v1"
}
