package dev.fajar.hris.reporting.delivery.responses

import dev.fajar.hris.reporting.domain.entities.ReportKind

data class ReportCatalogueEntryResponse(val kind: ReportKind, val definitionVersion: String)
