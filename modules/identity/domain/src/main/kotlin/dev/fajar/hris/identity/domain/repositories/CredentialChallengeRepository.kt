package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant
import java.util.UUID

interface CredentialChallengeRepository {
    fun findAccountByEmail(email: String): Result<CredentialAccount?>

    fun lockAccount(id: UUID): Result<CredentialAccount?>

    fun createPendingAccount(
        id: UUID,
        email: String,
        displayName: String,
    ): Result<CredentialAccount>

    fun renewInvitation(
        id: UUID,
        expectedVersion: Long,
        displayName: String,
    ): Result<CredentialAccount>

    fun findByToken(token: String): Result<CredentialChallenge?>

    fun find(id: UUID): Result<CredentialChallenge?>

    fun pending(accountId: UUID, kind: CredentialChallengeKind): Result<CredentialChallenge?>

    fun revokePending(accountId: UUID, at: Instant): Result<Unit>

    fun issue(challenge: CredentialChallenge): Result<IssuedCredentialLink>

    fun setPassword(
        id: UUID,
        expectedVersion: Long,
        password: String,
        active: Boolean,
    ): Result<MutationReceipt>

    fun consume(id: UUID, at: Instant): Result<Unit>

    fun purgeExpired(before: Instant, limit: Int): Result<Int>
}
