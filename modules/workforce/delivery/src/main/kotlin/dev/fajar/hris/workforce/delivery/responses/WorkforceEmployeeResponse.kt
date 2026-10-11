package dev.fajar.hris.workforce.delivery.responses

import java.util.UUID

data class WorkforceEmployeeResponse(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val status: String,
    val version: Long,
)
