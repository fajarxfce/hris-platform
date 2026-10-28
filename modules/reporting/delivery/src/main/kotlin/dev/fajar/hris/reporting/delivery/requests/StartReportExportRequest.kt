package dev.fajar.hris.reporting.delivery.requests

import dev.fajar.hris.reporting.domain.entities.ReportExportDataset
import dev.fajar.hris.reporting.domain.entities.ReportKind

data class StartReportExportRequest(
    val kind: ReportKind,
    val definitionVersion: String,
    val parameters: ReportFilterParametersRequest,
    val dataset: ReportExportDataset,
)

data class ChangeReportExportRequest(val expectedVersion: Long)
