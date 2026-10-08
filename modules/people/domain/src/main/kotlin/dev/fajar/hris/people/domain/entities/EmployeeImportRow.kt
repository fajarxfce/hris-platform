package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class EmployeeImportRow(
    val number: Int,
    val employeeNumber: String,
    val legalName: String,
    val draft: EmployeeDraft?,
    val parseIssues: Map<String, String>,
    val status: EmployeeImportRowStatus = EmployeeImportRowStatus.PENDING,
    val issues: Map<String, String> = emptyMap(),
    val createdEmploymentId: UUID? = null,
)
