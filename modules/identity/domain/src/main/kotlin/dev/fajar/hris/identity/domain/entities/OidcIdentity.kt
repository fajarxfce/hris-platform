package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class OidcIdentity(
    val id: UUID,
    val accountId: UUID,
    val issuer: String,
    val subject: String,
    val active: Boolean,
    val version: Long,
    val linkedAt: Instant,
    val linkedBy: UUID?,
)
