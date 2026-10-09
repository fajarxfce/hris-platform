package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.time.LocalDate

data class GroupHeadcountReport(
    val asOf: LocalDate,
    val evaluatedAt: Instant,
    val counts: GroupHeadcountCounts,
) {
    val definitionVersion: String = "headcount.v1"
}
