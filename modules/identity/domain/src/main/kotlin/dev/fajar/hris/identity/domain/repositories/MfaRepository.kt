package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant
import java.util.UUID

interface MfaRepository {
    fun advanceSecurityVersion(accountId: UUID, expected: Long): Result<Long>

    fun available(): Boolean

    fun lock(accountId: UUID): Result<MfaCredential?>

    fun startEnrollment(
        account: Account,
        operationId: UUID,
        expiresAt: Instant,
    ): Result<MfaEnrollment>

    fun enrollment(account: Account): Result<MfaEnrollment?>

    fun takeAttempt(accountId: UUID, windowStart: Instant, maximum: Int): Result<Boolean>

    fun matchEnrollment(accountId: UUID, code: String, at: Instant): Result<Long?>

    fun activate(
        accountId: UUID,
        operationId: UUID,
        expectedSecurityVersion: Long,
        counter: Long,
    ): Result<Long>

    fun matchAuthenticator(accountId: UUID, code: String, at: Instant): Result<Long?>

    fun recordCounter(
        accountId: UUID,
        expectedSecurityVersion: Long,
        counter: Long,
    ): Result<Boolean>

    fun consumeRecovery(accountId: UUID, code: String, at: Instant): Result<Boolean>

    fun replaceRecoveryCodes(accountId: UUID, at: Instant, count: Int): Result<List<String>>
}
