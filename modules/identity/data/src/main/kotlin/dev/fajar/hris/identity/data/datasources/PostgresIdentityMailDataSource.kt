package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.identity.data.queries.identityMailFence
import dev.fajar.hris.schema.Tables.IDENTITY_MAIL_DELIVERIES as D
import dev.fajar.hris.schema.tables.records.IdentityMailDeliveriesRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresIdentityMailDataSource(private val sql: DSLContext) : IdentityMailDataSource {
    override fun insert(record: IdentityMailDeliveriesRecord) {
        sql.executeInsert(record)
    }

    override fun supersedePending(accountId: UUID, at: Instant) {
        sql.update(D)
            .set(D.STATE, "SUPERSEDED")
            .setNull(D.TOKEN_ENCRYPTED)
            .set(D.AVAILABLE_AT, at.atOffset(UTC))
            .setNull(D.LEASE_OWNER)
            .setNull(D.LEASE_TOKEN)
            .setNull(D.LEASE_UNTIL)
            .where(D.ACCOUNT_ID.eq(accountId))
            .and(D.STATE.`in`("PENDING", "LEASED"))
            .execute()
    }

    override fun find(id: UUID) = sql.selectFrom(D).where(D.CHALLENGE_ID.eq(id)).fetchOne()

    override fun exhausted(now: Instant, maximumAttempts: Int, limit: Int) =
        sql.selectFrom(D)
            .where(
                D.STATE.eq("PENDING")
                    .or(D.STATE.eq("LEASED").and(D.LEASE_UNTIL.le(now.atOffset(UTC))))
            )
            .and(D.ATTEMPTS.ge(maximumAttempts).or(D.EXPIRES_AT.le(now.atOffset(UTC))))
            .orderBy(D.AVAILABLE_AT, D.CHALLENGE_ID)
            .limit(limit)
            .forUpdate()
            .skipLocked()
            .fetch()

    override fun failUnleased(id: UUID, now: Instant, code: String) =
        sql.update(D)
            .set(D.STATE, "FAILED")
            .setNull(D.TOKEN_ENCRYPTED)
            .setNull(D.LEASE_OWNER)
            .setNull(D.LEASE_TOKEN)
            .setNull(D.LEASE_UNTIL)
            .set(D.FAILURE_CODE, code)
            .set(D.AVAILABLE_AT, now.atOffset(UTC))
            .where(D.CHALLENGE_ID.eq(id))
            .and(
                D.STATE.eq("PENDING")
                    .or(D.STATE.eq("LEASED").and(D.LEASE_UNTIL.le(now.atOffset(UTC))))
            )
            .execute() == 1

    override fun claim(
        owner: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): List<IdentityMailDeliveriesRecord> =
        sql.selectFrom(
                DSL.table(
                    "claim_identity_mail({0},{1},{2},{3})",
                    DSL.`val`(owner),
                    DSL.`val`(limit),
                    DSL.`val`(leaseSeconds),
                    DSL.`val`(maximumAttempts),
                )
            )
            .fetchInto(IdentityMailDeliveriesRecord::class.java)

    override fun lock(id: UUID, owner: UUID, token: UUID) =
        sql.selectFrom(D).where(identityMailFence(id, owner, token)).forUpdate().fetchOne()

    override fun token(id: UUID, owner: UUID, token: UUID): String? =
        sql.select(D.TOKEN_ENCRYPTED)
            .from(D)
            .where(identityMailFence(id, owner, token))
            .fetchOne(D.TOKEN_ENCRYPTED)

    override fun finish(
        id: UUID,
        owner: UUID,
        token: UUID,
        state: String,
        availableAt: Instant,
        code: String?,
        discardToken: Boolean,
        at: Instant,
    ): Boolean =
        sql.update(D)
            .set(D.STATE, state)
            .set(D.AVAILABLE_AT, availableAt.atOffset(UTC))
            .set(D.FAILURE_CODE, code)
            .set(
                D.TOKEN_ENCRYPTED,
                if (discardToken) DSL.`val`(null as String?) else D.TOKEN_ENCRYPTED,
            )
            .set(D.DELIVERED_AT, if (state == "SENT") at.atOffset(UTC) else null)
            .setNull(D.LEASE_OWNER)
            .setNull(D.LEASE_TOKEN)
            .setNull(D.LEASE_UNTIL)
            .where(identityMailFence(id, owner, token))
            .execute() == 1
}
