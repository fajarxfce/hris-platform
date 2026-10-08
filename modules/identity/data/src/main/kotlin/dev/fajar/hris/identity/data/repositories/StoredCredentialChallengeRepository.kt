package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.*
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.mappers.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository
import java.time.Instant
import java.util.UUID

class StoredCredentialChallengeRepository(
    private val source: CredentialStoreDataSource,
    private val passwords: PasswordDataSource,
    private val tokens: IdentityTokenDataSource,
) : CredentialChallengeRepository {
    override fun findAccountByEmail(email: String): Result<CredentialAccount?> = safeDatabaseCall {
        source.findAccountByEmail(email)?.toCredentialAccount()
    }

    override fun lockAccount(id: UUID): Result<CredentialAccount?> = safeDatabaseCall {
        source.lockAccount(id)?.toCredentialAccount()
    }

    override fun createPendingAccount(
        id: UUID,
        email: String,
        displayName: String,
    ): Result<CredentialAccount> = safeDatabaseCall {
        source.insertAccount(id, email, displayName)
        checkNotNull(source.findAccount(id)).toCredentialAccount()
    }

    override fun renewInvitation(
        id: UUID,
        expectedVersion: Long,
        displayName: String,
    ): Result<CredentialAccount> =
        safeDatabaseCall { source.renewInvitation(id, expectedVersion, displayName) }
            .requireCurrentVersion()
            .flatMap {
                safeDatabaseCall { checkNotNull(source.findAccount(id)).toCredentialAccount() }
            }

    override fun findByToken(token: String): Result<CredentialChallenge?> = safeIdentityCall {
        source.findByHash(tokens.hash(token))?.toCredentialChallenge()
    }

    override fun find(id: UUID): Result<CredentialChallenge?> = safeDatabaseCall {
        source.findChallenge(id)?.toCredentialChallenge()
    }

    override fun pending(
        accountId: UUID,
        kind: CredentialChallengeKind,
    ): Result<CredentialChallenge?> = safeDatabaseCall {
        source.pending(accountId, kind.name)?.toCredentialChallenge()
    }

    override fun revokePending(accountId: UUID, at: Instant): Result<Unit> = safeDatabaseCall {
        source.revokePending(accountId, at)
    }

    override fun issue(challenge: CredentialChallenge): Result<IssuedCredentialLink> =
        safeIdentityCall {
            val token = tokens.generate()
            source.insertChallenge(challenge.toRecord(tokens.hash(token)))
            IssuedCredentialLink(challenge, token)
        }

    override fun setPassword(
        id: UUID,
        expectedVersion: Long,
        password: String,
        active: Boolean,
    ): Result<MutationReceipt> =
        safePasswordCall { checkNotNull(passwords.hash(password)) }
            .flatMap { hash ->
                safeDatabaseCall { source.setPassword(id, expectedVersion, hash, active) }
                    .requireCurrentVersion()
                    .map { MutationReceipt(id, it) }
            }

    override fun consume(id: UUID, at: Instant): Result<Unit> =
        safeDatabaseCall { source.consume(id, at) }
            .flatMap {
                if (it) Result.Success(Unit)
                else Result.Failed(Failure(FailureKind.CONFLICT, "credential_link_unavailable"))
            }

    override fun purgeExpired(before: Instant, limit: Int): Result<Int> = safeDatabaseCall {
        source.purgeExpired(before, limit)
    }
}
