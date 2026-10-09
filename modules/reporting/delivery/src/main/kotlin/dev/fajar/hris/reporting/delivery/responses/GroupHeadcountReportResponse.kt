package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.time.LocalDate

data class GroupHeadcountReportResponse(
    val asOf: LocalDate,
    val evaluatedAt: Instant,
    val definitionVersion: String,
    val totals: HeadcountCountsResponse,
    val companies: List<CompanyHeadcountResponse>,
)
