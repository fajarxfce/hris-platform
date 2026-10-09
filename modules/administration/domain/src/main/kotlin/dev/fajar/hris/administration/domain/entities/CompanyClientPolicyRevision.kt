package dev.fajar.hris.administration.domain.entities

import java.time.Instant
import java.util.UUID

data class CompanyClientPolicyRevision(
    val version: Long,
    val activateAt: Instant,
    val policy: ClientPolicy,
    val recordedAt: Instant,
    val actorId: UUID,
    val reason: String,
)
