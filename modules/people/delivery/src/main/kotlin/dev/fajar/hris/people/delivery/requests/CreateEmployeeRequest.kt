package dev.fajar.hris.people.delivery.requests

import java.util.UUID

data class CreateEmployeeRequest(
    val id: UUID,
    val employeeNumber: String,
    val person: PersonRequest,
    val terms: TermsRequest,
    val reason: String,
)
