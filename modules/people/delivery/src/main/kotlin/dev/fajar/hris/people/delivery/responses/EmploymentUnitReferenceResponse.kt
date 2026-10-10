package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class EmploymentUnitReferenceResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val active: Boolean,
)
