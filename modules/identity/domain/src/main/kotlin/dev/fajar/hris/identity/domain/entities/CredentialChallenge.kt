package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class CredentialChallenge(
    val id: UUID,
    val accountId: UUID,
    val kind: CredentialChallengeKind,
    val credentialVersion: Long,
    val createdBy: UUID,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val consumedAt: Instant?,
    val revokedAt: Instant?,
)
