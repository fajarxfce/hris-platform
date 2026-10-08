package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.Accounts.ACCOUNTS as A
import dev.fajar.hris.schema.tables.MfaAttempts.MFA_ATTEMPTS as T
import dev.fajar.hris.schema.tables.MfaEnrollments.MFA_ENROLLMENTS as E
import dev.fajar.hris.schema.tables.MfaRecoveryCodes.MFA_RECOVERY_CODES as R
import dev.fajar.hris.schema.tables.records.*
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresMfaStoreDataSource(private val sql: DSLContext) : MfaStoreDataSource {
    override fun advanceSecurityVersion(accountId: UUID, expected: Long): Long? =
        sql.update(A)
            .set(A.SECURITY_VERSION, expected + 1)
            .set(A.VERSION, A.VERSION.plus(1))
            .where(A.ID.eq(accountId))
            .and(A.SECURITY_VERSION.eq(expected))
            .returning(A.SECURITY_VERSION)
            .fetchOne()
            ?.securityVersion

    override fun lockAccount(accountId: UUID): AccountsRecord? =
        sql.selectFrom(A).where(A.ID.eq(accountId)).forUpdate().fetchOne()

    override fun account(accountId: UUID): AccountsRecord? =
        sql.selectFrom(A).where(A.ID.eq(accountId)).fetchOne()

    override fun enrollment(accountId: UUID): MfaEnrollmentsRecord? =
        sql.selectFrom(E).where(E.ACCOUNT_ID.eq(accountId)).fetchOne()

    override fun saveEnrollment(
        accountId: UUID,
        operationId: UUID,
        encrypted: String,
        expiresAt: Instant,
    ) {
        val expires = OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        sql.insertInto(E)
            .set(E.ACCOUNT_ID, accountId)
            .set(E.OPERATION_ID, operationId)
            .set(E.SECRET_ENCRYPTED, encrypted)
            .set(E.EXPIRES_AT, expires)
            .onConflict(E.ACCOUNT_ID)
            .doUpdate()
            .set(E.OPERATION_ID, operationId)
            .set(E.SECRET_ENCRYPTED, encrypted)
            .set(E.EXPIRES_AT, expires)
            .execute()
    }

    override fun removeEnrollment(accountId: UUID, operationId: UUID) {
        sql.deleteFrom(E)
            .where(E.ACCOUNT_ID.eq(accountId))
            .and(E.OPERATION_ID.eq(operationId))
            .execute()
    }

    override fun incrementAttempt(accountId: UUID, windowStart: Instant, maximum: Int): Boolean {
        val start = OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC)
        return sql.insertInto(T)
            .set(T.ACCOUNT_ID, accountId)
            .set(T.WINDOW_START, start)
            .set(T.ATTEMPTS, 1)
            .onConflict(T.ACCOUNT_ID)
            .doUpdate()
            .set(T.ATTEMPTS, DSL.`when`(T.WINDOW_START.lt(start), 1).otherwise(T.ATTEMPTS.plus(1)))
            .set(T.WINDOW_START, start)
            .where(
                T.WINDOW_START.le(start).and(T.WINDOW_START.lt(start).or(T.ATTEMPTS.lt(maximum)))
            )
            .returning(T.ACCOUNT_ID)
            .fetchOne() != null
    }

    override fun activate(
        accountId: UUID,
        expectedSecurityVersion: Long,
        encrypted: String,
        counter: Long,
    ): Long? =
        sql.update(A)
            .set(A.MFA_SECRET_ENCRYPTED, encrypted)
            .set(A.MFA_LAST_COUNTER, counter)
            .set(A.SECURITY_VERSION, A.SECURITY_VERSION.plus(1))
            .set(A.VERSION, A.VERSION.plus(1))
            .where(A.ID.eq(accountId))
            .and(A.SECURITY_VERSION.eq(expectedSecurityVersion))
            .and(A.MFA_SECRET_ENCRYPTED.isNull)
            .returning(A.SECURITY_VERSION)
            .fetchOne()
            ?.securityVersion

    override fun recordCounter(
        accountId: UUID,
        expectedSecurityVersion: Long,
        counter: Long,
    ): Boolean =
        sql.update(A)
            .set(A.MFA_LAST_COUNTER, counter)
            .where(A.ID.eq(accountId))
            .and(A.SECURITY_VERSION.eq(expectedSecurityVersion))
            .and(A.MFA_LAST_COUNTER.isNull.or(A.MFA_LAST_COUNTER.lt(counter)))
            .execute() == 1

    override fun consumeRecovery(accountId: UUID, hash: String, at: Instant): Boolean =
        sql.update(R)
            .set(R.USED_AT, OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
            .where(R.ACCOUNT_ID.eq(accountId))
            .and(R.CODE_HASH.eq(hash))
            .and(R.USED_AT.isNull)
            .execute() == 1

    override fun replaceRecovery(accountId: UUID, hashes: List<String>, at: Instant) {
        sql.deleteFrom(R).where(R.ACCOUNT_ID.eq(accountId)).execute()
        sql.batchInsert(
                hashes.map {
                    MfaRecoveryCodesRecord().apply {
                        this.accountId = accountId
                        codeHash = it
                        createdAt = OffsetDateTime.ofInstant(at, ZoneOffset.UTC)
                    }
                }
            )
            .execute()
    }
}
