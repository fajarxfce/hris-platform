package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.util.UUID

data class ReportCatalogueResponse(
    val companyId: UUID,
    val evaluatedAt: Instant,
    val reports: List<ReportCatalogueEntryResponse>,
)
