package dev.fajar.hris.workforce.delivery.responses

import java.util.UUID

data class WorkforceEmploymentResponse(
    val id: UUID,
    val employeeNumber: String,
    val status: String,
    val version: Long,
)
