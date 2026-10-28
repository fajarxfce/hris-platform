package dev.fajar.hris.reporting.delivery.mappers

import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.entities.SavedReportFilter

fun SavedReportFilter.toResponse(): SavedReportFilterResponse {
    val definition = requireNotNull(definition)
    val p = definition.parameters
    return SavedReportFilterResponse(
        id,
        companyId,
        definition.name,
        definition.kind,
        definition.definitionVersion,
        ReportFilterParametersResponse(p.asOf, p.from, p.until, p.status, p.companies),
        version,
        createdAt,
        updatedAt,
    )
}
