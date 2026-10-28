package dev.fajar.hris.reporting.delivery.requests

import dev.fajar.hris.reporting.domain.entities.ReportKind

data class SaveReportFilterRequest(
    val name: String,
    val kind: ReportKind,
    val definitionVersion: String,
    val parameters: ReportFilterParametersRequest,
    val expectedVersion: Long? = null,
)
