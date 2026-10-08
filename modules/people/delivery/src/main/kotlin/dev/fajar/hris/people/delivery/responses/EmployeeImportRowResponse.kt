package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class EmployeeImportRowResponse(
    val number: Int,
    val employeeNumber: String,
    val legalName: String,
    val status: String,
    val issues: Map<String, String>,
    val proposed: EmployeeImportProposalResponse?,
    val createdEmploymentId: UUID?,
)
