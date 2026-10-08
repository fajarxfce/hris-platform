package dev.fajar.hris.people.delivery.requests

import java.time.LocalDate

data class PersonProfileRequest(
    val expectedVersion: Long,
    val legalName: String,
    val birthDate: LocalDate? = null,
    val nationality: String,
    val email: String? = null,
    val reason: String,
)
