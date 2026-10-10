package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class EmploymentManagerReferenceResponse(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val working: Boolean,
)
