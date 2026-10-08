package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.Accounts.ACCOUNTS as A
import dev.fajar.hris.schema.tables.ConsumedRefreshTokens.CONSUMED_REFRESH_TOKENS as R
import dev.fajar.hris.schema.tables.NativeSessions.NATIVE_SESSIONS as S
import dev.fajar.hris.schema.tables.records.*
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext

class PostgresNativeSessionDataSource(private val sql: DSLContext) : NativeSessionDataSource {
    override fun lockAccount(accountId: UUID): AccountsRecord? =
        sql.selectFrom(A).where(A.ID.eq(accountId)).forUpdate().fetchOne()

    override fun byAccess(hash: String): NativeSessionsRecord? =
        sql.selectFrom(S).where(S.ACCESS_HASH.eq(hash)).fetchOne()

    override fun byRefresh(hash: String): NativeSessionsRecord? =
        sql.selectFrom(S).where(S.REFRESH_HASH.eq(hash)).fetchOne()

    override fun consumed(hash: String): ConsumedRefreshTokensRecord? =
        sql.selectFrom(R).where(R.TOKEN_HASH.eq(hash)).fetchOne()

    override fun get(sessionId: UUID, accountId: UUID?, lock: Boolean): NativeSessionsRecord? {
        val query = sql.selectFrom(S).where(S.ID.eq(sessionId))
        if (accountId != null) query.and(S.ACCOUNT_ID.eq(accountId))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun exchange(accountId: UUID, operationId: UUID): NativeSessionsRecord? =
        sql.selectFrom(S)
            .where(S.ACCOUNT_ID.eq(accountId))
            .and(S.EXCHANGE_OPERATION_ID.eq(operationId))
            .fetchOne()

    override fun list(accountId: UUID, at: Instant, limit: Int): List<NativeSessionsRecord> =
        sql.selectFrom(S)
            .where(S.ACCOUNT_ID.eq(accountId))
            .and(S.REVOKED_AT.isNull)
            .and(S.EXPIRES_AT.gt(OffsetDateTime.ofInstant(at, ZoneOffset.UTC)))
            .and(
                S.CREDENTIAL_VERSION.eq(
                    sql.select(A.SECURITY_VERSION).from(A).where(A.ID.eq(accountId))
                )
            )
            .orderBy(S.CREATED_AT.desc(), S.ID)
            .limit(limit)
            .fetch()

    override fun countIssued(accountId: UUID, at: Instant): Long =
        sql.selectCount()
            .from(S)
            .where(S.ACCOUNT_ID.eq(accountId))
            .and(S.EXPIRES_AT.gt(OffsetDateTime.ofInstant(at, ZoneOffset.UTC)))
            .fetchOne(0, Long::class.java) ?: 0

    override fun insert(record: NativeSessionsRecord) {
        sql.insertInto(S).set(record).execute()
    }

    override fun consume(record: ConsumedRefreshTokensRecord) {
        sql.insertInto(R).set(record).execute()
    }

    override fun update(
        record: NativeSessionsRecord,
        expectedVersion: Long,
        expectedRefreshHash: String,
    ): Int =
        sql.update(S)
            .set(record)
            .where(S.ID.eq(record.id))
            .and(S.ACCOUNT_ID.eq(record.accountId))
            .and(S.VERSION.eq(expectedVersion))
            .and(S.REFRESH_HASH.eq(expectedRefreshHash))
            .and(S.REVOKED_AT.isNull)
            .execute()

    override fun revoke(sessionId: UUID, accountId: UUID, at: Instant): Int =
        sql.update(S)
            .set(S.REVOKED_AT, OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
            .setNull(S.EXCHANGE_ENCRYPTED)
            .setNull(S.EXCHANGE_REPLAY_UNTIL)
            .where(S.ID.eq(sessionId))
            .and(S.ACCOUNT_ID.eq(accountId))
            .and(S.REVOKED_AT.isNull)
            .execute()

    override fun purgeExpired(accountId: UUID, before: Instant, limit: Int) {
        val ids =
            sql.select(S.ID)
                .from(S)
                .where(S.ACCOUNT_ID.eq(accountId))
                .and(S.EXPIRES_AT.le(OffsetDateTime.ofInstant(before, ZoneOffset.UTC)))
                .orderBy(S.EXPIRES_AT, S.ID)
                .limit(limit)
        sql.deleteFrom(S).where(S.ID.`in`(ids)).execute()
    }

    override fun clearExpiredReplays(sessionId: UUID, before: Instant) {
        sql.update(R)
            .setNull(R.REPLAY_ENCRYPTED)
            .where(R.SESSION_ID.eq(sessionId))
            .and(R.REPLAY_UNTIL.le(OffsetDateTime.ofInstant(before, ZoneOffset.UTC)))
            .and(R.REPLAY_ENCRYPTED.isNotNull)
            .execute()
    }
}
