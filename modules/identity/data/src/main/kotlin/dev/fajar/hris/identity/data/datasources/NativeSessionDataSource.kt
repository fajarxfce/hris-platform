package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.Instant
import java.util.UUID

interface NativeSessionDataSource {
    fun lockAccount(accountId: UUID): AccountsRecord?

    fun byAccess(hash: String): NativeSessionsRecord?

    fun byRefresh(hash: String): NativeSessionsRecord?

    fun consumed(hash: String): ConsumedRefreshTokensRecord?

    fun get(sessionId: UUID, accountId: UUID? = null, lock: Boolean = false): NativeSessionsRecord?

    fun exchange(accountId: UUID, operationId: UUID): NativeSessionsRecord?

    fun list(accountId: UUID, at: Instant, limit: Int): List<NativeSessionsRecord>

    fun countIssued(accountId: UUID, at: Instant): Long

    fun insert(record: NativeSessionsRecord)

    fun consume(record: ConsumedRefreshTokensRecord)

    fun update(
        record: NativeSessionsRecord,
        expectedVersion: Long,
        expectedRefreshHash: String,
    ): Int

    fun revoke(sessionId: UUID, accountId: UUID, at: Instant): Int

    fun purgeExpired(accountId: UUID, before: Instant, limit: Int)

    fun clearExpiredReplays(sessionId: UUID, before: Instant)
}
