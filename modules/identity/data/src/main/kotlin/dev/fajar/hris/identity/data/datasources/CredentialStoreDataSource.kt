package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.records.IdentityChallengesRecord
import java.time.Instant
import java.util.UUID

interface CredentialStoreDataSource {
    fun findAccountByEmail(email: String): CredentialAccountRow?

    fun findAccount(id: UUID): CredentialAccountRow?

    fun lockAccount(id: UUID): CredentialAccountRow?

    fun insertAccount(id: UUID, email: String, displayName: String)

    fun renewInvitation(id: UUID, expectedVersion: Long, displayName: String): Long?

    fun findByHash(hash: String): IdentityChallengesRecord?

    fun findChallenge(id: UUID): IdentityChallengesRecord?

    fun pending(accountId: UUID, kind: String): IdentityChallengesRecord?

    fun revokePending(accountId: UUID, at: Instant)

    fun insertChallenge(record: IdentityChallengesRecord)

    fun setPassword(id: UUID, expectedVersion: Long, hash: String, active: Boolean): Long?

    fun consume(id: UUID, at: Instant): Boolean

    fun purgeExpired(before: Instant, limit: Int): Int
}
