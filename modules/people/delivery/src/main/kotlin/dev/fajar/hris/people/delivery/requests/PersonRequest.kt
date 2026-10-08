package dev.fajar.hris.people.delivery.requests

import java.time.LocalDate
import java.util.UUID

data class PersonRequest(
    val id: UUID,
    val accountId: UUID? = null,
    val legalName: String,
    val birthDate: LocalDate? = null,
    val nationality: String,
    val email: String? = null,
)
