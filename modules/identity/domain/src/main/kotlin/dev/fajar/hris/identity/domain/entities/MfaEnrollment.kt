package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class MfaEnrollment(
    val operationId: UUID,
    val secret: String,
    val email: String,
    val expiresAt: Instant,
) {
    override fun toString(): String = "MfaEnrollment(<redacted>)"
}
