package dev.fajar.hris.reporting.domain.entities

data class ReportSelection(
    val kind: ReportKind,
    val definitionVersion: String,
    val parameters: ReportFilterParameters,
)
