package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant
import java.util.UUID

interface NativeSessionRepository {
    fun lockAccount(accountId: UUID): Result<Account?>

    fun findAccess(token: String): Result<NativeSession?>

    fun findRefresh(token: String): Result<NativeRefresh?>

    fun lock(sessionId: UUID, accountId: UUID): Result<NativeSession?>

    fun findExchange(accountId: UUID, operationId: UUID): Result<NativeSession?>

    fun replayExchange(session: NativeSession): Result<NativeTokens?>

    fun replayRefresh(
        token: String,
        session: NativeSession,
        operationId: UUID,
    ): Result<NativeTokens?>

    fun list(accountId: UUID, at: Instant, limit: Int): Result<List<NativeSession>>

    fun countIssued(accountId: UUID, at: Instant): Result<Long>

    fun create(session: NativeSession, replayUntil: Instant): Result<NativeTokens>

    fun rotate(
        session: NativeSession,
        oldToken: String?,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<NativeTokens>

    fun revoke(sessionId: UUID, accountId: UUID, at: Instant): Result<Boolean>

    fun purgeExpired(accountId: UUID, before: Instant, limit: Int): Result<Unit>
}
