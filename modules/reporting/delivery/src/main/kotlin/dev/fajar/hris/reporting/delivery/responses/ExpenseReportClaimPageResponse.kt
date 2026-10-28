package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ExpenseReportClaimPageResponse(
    val companyId: UUID,
    val from: LocalDate,
    val until: LocalDate,
    val status: String?,
    val evaluatedAt: Instant,
    val definitionVersion: String,
    val items: List<ExpenseReportClaimResponse>,
    val nextCursor: String?,
    val currency: String = "IDR",
)
