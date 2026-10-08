package dev.fajar.hris.people.domain.entities

import java.time.LocalDate
import java.util.UUID

data class PersonProfile(
    val id: UUID,
    val accountId: UUID?,
    val legalName: String,
    val birthDate: LocalDate?,
    val nationality: String,
    val email: String?,
)
