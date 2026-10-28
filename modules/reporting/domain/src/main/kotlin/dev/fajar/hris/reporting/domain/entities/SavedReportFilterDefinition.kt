package dev.fajar.hris.reporting.domain.entities

data class SavedReportFilterDefinition(
    val name: String,
    val kind: ReportKind,
    val definitionVersion: String,
    val parameters: ReportFilterParameters,
)
