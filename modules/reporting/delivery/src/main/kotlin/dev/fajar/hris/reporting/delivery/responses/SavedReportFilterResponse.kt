package dev.fajar.hris.reporting.delivery.responses

import dev.fajar.hris.reporting.domain.entities.ReportKind
import java.time.Instant
import java.util.UUID

data class SavedReportFilterResponse(
    val id: UUID,
    val companyId: UUID,
    val name: String,
    val kind: ReportKind,
    val definitionVersion: String,
    val parameters: ReportFilterParametersResponse,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)
