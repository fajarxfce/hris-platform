package dev.fajar.hris.identity.delivery.responses

import java.time.Instant
import java.util.UUID

data class OidcIdentityResponse(
    val id: UUID,
    val issuer: String,
    val subject: String,
    val active: Boolean,
    val version: Long,
    val linkedAt: Instant,
)
