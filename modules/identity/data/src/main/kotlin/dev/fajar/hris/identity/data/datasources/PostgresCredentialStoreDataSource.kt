package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.identity.data.queries.*
import dev.fajar.hris.schema.Tables.ACCOUNTS as A
import dev.fajar.hris.schema.Tables.IDENTITY_CHALLENGES as C
import dev.fajar.hris.schema.tables.records.IdentityChallengesRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID
import org.jooq.DSLContext

class PostgresCredentialStoreDataSource(private val sql: DSLContext) : CredentialStoreDataSource {
    override fun findAccountByEmail(email: String) =
        selectCredentialAccounts(sql).where(A.EMAIL.eq(email)).fetchOne {
            it.toCredentialAccountRow()
        }

    override fun findAccount(id: UUID) =
        selectCredentialAccounts(sql).where(A.ID.eq(id)).fetchOne { it.toCredentialAccountRow() }

    override fun lockAccount(id: UUID) =
        selectCredentialAccounts(sql).where(A.ID.eq(id)).forUpdate().of(A).fetchOne {
            it.toCredentialAccountRow()
        }

    override fun insertAccount(id: UUID, email: String, displayName: String) {
        sql.insertInto(A)
            .set(A.ID, id)
            .set(A.EMAIL, email)
            .set(A.DISPLAY_NAME, displayName)
            .set(A.ACTIVE, false)
            .set(A.INVITATION_PENDING, true)
            .execute()
    }

    override fun renewInvitation(id: UUID, expectedVersion: Long, displayName: String): Long? =
        sql.update(A)
            .set(A.DISPLAY_NAME, displayName)
            .set(A.INVITATION_PENDING, true)
            .set(A.VERSION, expectedVersion + 1)
            .set(A.SECURITY_VERSION, A.SECURITY_VERSION.plus(1))
            .where(A.ID.eq(id).and(A.VERSION.eq(expectedVersion)))
            .returning(A.VERSION)
            .fetchOne()
            ?.version

    override fun findByHash(hash: String) =
        sql.selectFrom(C).where(C.TOKEN_HASH.eq(hash)).fetchOne()

    override fun findChallenge(id: UUID) = sql.selectFrom(C).where(C.ID.eq(id)).fetchOne()

    override fun pending(accountId: UUID, kind: String) =
        sql.selectFrom(C)
            .where(C.ACCOUNT_ID.eq(accountId))
            .and(C.KIND.eq(kind))
            .and(C.CONSUMED_AT.isNull)
            .and(C.REVOKED_AT.isNull)
            .fetchOne()

    override fun revokePending(accountId: UUID, at: Instant) {
        sql.update(C)
            .set(C.REVOKED_AT, at.atOffset(UTC))
            .where(C.ACCOUNT_ID.eq(accountId))
            .and(C.CONSUMED_AT.isNull)
            .and(C.REVOKED_AT.isNull)
            .execute()
    }

    override fun insertChallenge(record: IdentityChallengesRecord) {
        sql.executeInsert(record)
    }

    override fun setPassword(
        id: UUID,
        expectedVersion: Long,
        hash: String,
        active: Boolean,
    ): Long? =
        sql.update(A)
            .set(A.PASSWORD_HASH, hash)
            .set(A.ACTIVE, active)
            .set(A.INVITATION_PENDING, false)
            .set(A.VERSION, expectedVersion + 1)
            .set(A.SECURITY_VERSION, A.SECURITY_VERSION.plus(1))
            .where(A.ID.eq(id).and(A.VERSION.eq(expectedVersion)))
            .returning(A.VERSION)
            .fetchOne()
            ?.version

    override fun consume(id: UUID, at: Instant): Boolean =
        sql.update(C)
            .set(C.CONSUMED_AT, at.atOffset(UTC))
            .where(C.ID.eq(id))
            .and(C.CONSUMED_AT.isNull)
            .and(C.REVOKED_AT.isNull)
            .execute() == 1

    override fun purgeExpired(before: Instant, limit: Int): Int =
        sql.deleteFrom(C)
            .where(
                C.ID.`in`(
                    sql.select(C.ID)
                        .from(C)
                        .where(C.EXPIRES_AT.lt(before.atOffset(UTC)))
                        .orderBy(C.EXPIRES_AT, C.ID)
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                )
            )
            .execute()
}
