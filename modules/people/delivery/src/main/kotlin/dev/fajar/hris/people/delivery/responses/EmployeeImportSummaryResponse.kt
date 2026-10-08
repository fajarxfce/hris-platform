package dev.fajar.hris.people.delivery.responses

data class EmployeeImportSummaryResponse(
    val batch: EmployeeImportResponse,
    val counts: Map<String, Int>,
)
