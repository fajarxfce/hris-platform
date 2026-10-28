package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.util.UUID

data class ReportCatalogue(
    val companyId: UUID,
    val evaluatedAt: Instant,
    val reports: List<ReportKind>,
)
