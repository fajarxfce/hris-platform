package dev.fajar.hris.people.delivery.requests

import java.util.UUID

data class EmployeeImportRequest(
    val id: UUID,
    val fileName: String,
    val csv: String,
    val reason: String,
)
