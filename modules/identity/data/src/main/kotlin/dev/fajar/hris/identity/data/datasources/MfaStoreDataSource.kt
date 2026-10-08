package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.Instant
import java.util.UUID

interface MfaStoreDataSource {
    fun advanceSecurityVersion(accountId: UUID, expected: Long): Long?

    fun lockAccount(accountId: UUID): AccountsRecord?

    fun account(accountId: UUID): AccountsRecord?

    fun enrollment(accountId: UUID): MfaEnrollmentsRecord?

    fun saveEnrollment(accountId: UUID, operationId: UUID, encrypted: String, expiresAt: Instant)

    fun removeEnrollment(accountId: UUID, operationId: UUID)

    fun incrementAttempt(accountId: UUID, windowStart: Instant, maximum: Int): Boolean

    fun activate(
        accountId: UUID,
        expectedSecurityVersion: Long,
        encrypted: String,
        counter: Long,
    ): Long?

    fun recordCounter(accountId: UUID, expectedSecurityVersion: Long, counter: Long): Boolean

    fun consumeRecovery(accountId: UUID, hash: String, at: Instant): Boolean

    fun replaceRecovery(accountId: UUID, hashes: List<String>, at: Instant)
}
