package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class PendingMfaEnrollment(val operationId: UUID, val expiresAt: Instant)
