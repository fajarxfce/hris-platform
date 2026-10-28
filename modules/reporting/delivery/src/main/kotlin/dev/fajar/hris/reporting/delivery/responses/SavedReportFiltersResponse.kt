package dev.fajar.hris.reporting.delivery.responses

import java.util.UUID

data class SavedReportFiltersResponse(
    val companyId: UUID,
    val items: List<SavedReportFilterResponse>,
)
