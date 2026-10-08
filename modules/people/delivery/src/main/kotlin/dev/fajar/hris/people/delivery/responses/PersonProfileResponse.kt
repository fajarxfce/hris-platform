package dev.fajar.hris.people.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class PersonProfileResponse(
    val personId: UUID,
    val ownerCompanyId: UUID,
    val accountId: UUID?,
    val legalName: String,
    val birthDate: LocalDate?,
    val nationality: String,
    val email: String?,
    val version: Long,
)
