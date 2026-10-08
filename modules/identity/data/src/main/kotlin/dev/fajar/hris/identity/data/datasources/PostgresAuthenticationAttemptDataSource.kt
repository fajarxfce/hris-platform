package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.AuthenticationAttempts.AUTHENTICATION_ATTEMPTS as A
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresAuthenticationAttemptDataSource(private val sql: DSLContext) :
    AuthenticationAttemptDataSource {
    override fun purgeExpired(before: Instant, limit: Int): Int =
        sql.deleteFrom(A)
            .where(
                A.BUCKET_HASH.`in`(
                    sql.select(A.BUCKET_HASH)
                        .from(A)
                        .where(A.EXPIRES_AT.le(OffsetDateTime.ofInstant(before, ZoneOffset.UTC)))
                        .orderBy(A.EXPIRES_AT)
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                )
            )
            .execute()

    override fun increment(
        bucketHash: String,
        windowStart: Instant,
        expiresAt: Instant,
        maximum: Int,
    ): Boolean {
        val start = OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC)
        val expires = OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        return sql.insertInto(A)
            .set(A.BUCKET_HASH, bucketHash)
            .set(A.WINDOW_START, start)
            .set(A.EXPIRES_AT, expires)
            .set(A.ATTEMPTS, 1)
            .onConflict(A.BUCKET_HASH)
            .doUpdate()
            .set(A.ATTEMPTS, DSL.`when`(A.WINDOW_START.lt(start), 1).otherwise(A.ATTEMPTS.plus(1)))
            .set(A.WINDOW_START, start)
            .set(A.EXPIRES_AT, expires)
            .where(
                A.WINDOW_START.le(start).and(A.WINDOW_START.lt(start).or(A.ATTEMPTS.lt(maximum)))
            )
            .returning(A.BUCKET_HASH)
            .fetchOne() != null
    }
}
