package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.util.UUID

data class PersonProfileRevision(
    val revision: Long,
    val profile: PersonProfile,
    val actorId: UUID?,
    val reason: String,
    val recordedAt: Instant,
)
