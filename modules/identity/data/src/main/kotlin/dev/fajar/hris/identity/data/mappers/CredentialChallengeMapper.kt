package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.data.datasources.CredentialAccountRow
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.schema.tables.records.IdentityChallengesRecord
import java.time.ZoneOffset.UTC

fun CredentialAccountRow.toCredentialAccount() =
    CredentialAccount(
        id,
        email,
        displayName,
        active,
        invitationPending,
        hasPassword,
        version,
        securityVersion,
    )

fun IdentityChallengesRecord.toCredentialChallenge() =
    CredentialChallenge(
        id,
        accountId,
        CredentialChallengeKind.valueOf(kind),
        credentialVersion,
        createdBy,
        issuedAt.toInstant(),
        expiresAt.toInstant(),
        consumedAt?.toInstant(),
        revokedAt?.toInstant(),
    )

fun CredentialChallenge.toRecord(hash: String) =
    IdentityChallengesRecord().also {
        it.id = id
        it.accountId = accountId
        it.kind = kind.name
        it.tokenHash = hash
        it.credentialVersion = credentialVersion
        it.createdBy = createdBy
        it.issuedAt = issuedAt.atOffset(UTC)
        it.expiresAt = expiresAt.atOffset(UTC)
        it.consumedAt = consumedAt?.atOffset(UTC)
        it.revokedAt = revokedAt?.atOffset(UTC)
    }
