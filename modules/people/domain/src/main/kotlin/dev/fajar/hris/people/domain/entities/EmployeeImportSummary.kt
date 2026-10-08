package dev.fajar.hris.people.domain.entities

data class EmployeeImportSummary(
    val batch: EmployeeImport,
    val counts: Map<EmployeeImportRowStatus, Int>,
)
