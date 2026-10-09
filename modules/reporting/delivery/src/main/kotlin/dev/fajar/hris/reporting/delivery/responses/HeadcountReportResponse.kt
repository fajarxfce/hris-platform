package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class HeadcountReportResponse(
    val companyId: UUID,
    val asOf: LocalDate,
    val evaluatedAt: Instant,
    val definitionVersion: String,
    val employments: Long,
    val persons: Long,
    val active: Long,
    val probation: Long,
    val suspended: Long,
    val permanent: Long,
    val fixedTerm: Long,
)
