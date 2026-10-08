package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PersonProfileRevisionResponse(
    val revision: Long,
    val legalName: String,
    val birthDate: LocalDate?,
    val nationality: String,
    val email: String?,
    val actorId: UUID?,
    val reason: String,
    val recordedAt: Instant,
    val accountId: UUID?,
)
