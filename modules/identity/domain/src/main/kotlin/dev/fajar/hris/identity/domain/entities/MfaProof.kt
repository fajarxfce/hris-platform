package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class MfaProof(val accountId: UUID, val securityVersion: Long, val verifiedAt: Instant)
