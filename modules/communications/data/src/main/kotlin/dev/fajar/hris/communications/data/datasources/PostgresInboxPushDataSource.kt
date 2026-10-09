package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.queries.inboxPushFence
import dev.fajar.hris.schema.tables.InboxPushDispatches.INBOX_PUSH_DISPATCHES as D
import dev.fajar.hris.schema.tables.records.InboxPushDispatchesRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresInboxPushDataSource(private val sql: DSLContext) : InboxPushDataSource {
    override fun claim(
        owner: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): List<InboxPushDispatchesRecord> =
        sql.selectFrom(
                DSL.table(
                    "claim_inbox_push({0},{1},{2},{3})",
                    DSL.`val`(owner),
                    DSL.`val`(limit),
                    DSL.`val`(leaseSeconds),
                    DSL.`val`(maximumAttempts),
                )
            )
            .fetchInto(InboxPushDispatchesRecord::class.java)

    override fun lock(
        companyId: UUID,
        inboxId: UUID,
        owner: UUID,
        token: UUID,
    ): InboxPushDispatchesRecord? =
        sql.selectFrom(D)
            .where(inboxPushFence(companyId, inboxId, owner, token))
            .forUpdate()
            .fetchOne()

    override fun pin(
        companyId: UUID,
        inboxId: UUID,
        owner: UUID,
        token: UUID,
        sessionId: UUID,
    ): Boolean =
        sql.update(D)
            .set(D.TARGET_SESSION_ID, sessionId)
            .where(inboxPushFence(companyId, inboxId, owner, token))
            .and(D.TARGET_SESSION_ID.isNull.or(D.TARGET_SESSION_ID.eq(sessionId)))
            .execute() == 1

    override fun advance(
        companyId: UUID,
        inboxId: UUID,
        owner: UUID,
        token: UUID,
        sessionId: UUID,
        accepted: Boolean,
        rejected: Boolean,
        code: String?,
        now: Instant,
    ): Boolean =
        sql.update(D)
            .set(D.STATE, "PENDING")
            .set(D.CURSOR_SESSION_ID, sessionId)
            .setNull(D.TARGET_SESSION_ID)
            .set(D.PROCESSED_COUNT, D.PROCESSED_COUNT.plus(1))
            .set(D.ACCEPTED_COUNT, D.ACCEPTED_COUNT.plus(if (accepted) 1 else 0))
            .set(D.REJECTED_COUNT, D.REJECTED_COUNT.plus(if (rejected) 1 else 0))
            .set(D.ATTEMPTS, 0)
            .set(D.AVAILABLE_AT, now.atOffset(UTC))
            .set(D.FAILURE_CODE, code)
            .setNull(D.LEASE_OWNER)
            .setNull(D.LEASE_TOKEN)
            .setNull(D.LEASE_UNTIL)
            .where(inboxPushFence(companyId, inboxId, owner, token))
            .and(D.TARGET_SESSION_ID.eq(sessionId))
            .execute() == 1

    override fun retry(
        companyId: UUID,
        inboxId: UUID,
        owner: UUID,
        token: UUID,
        availableAt: Instant,
        code: String,
    ): Boolean =
        sql.update(D)
            .set(D.STATE, "PENDING")
            .set(D.AVAILABLE_AT, availableAt.atOffset(UTC))
            .set(D.FAILURE_CODE, code)
            .setNull(D.LEASE_OWNER)
            .setNull(D.LEASE_TOKEN)
            .setNull(D.LEASE_UNTIL)
            .where(inboxPushFence(companyId, inboxId, owner, token))
            .execute() == 1

    override fun finish(
        companyId: UUID,
        inboxId: UUID,
        owner: UUID,
        token: UUID,
        state: String,
        code: String?,
        now: Instant,
    ): Boolean =
        sql.update(D)
            .set(D.STATE, state)
            .set(D.FINISHED_AT, now.atOffset(UTC))
            .set(D.FAILURE_CODE, code)
            .setNull(D.TARGET_SESSION_ID)
            .setNull(D.LEASE_OWNER)
            .setNull(D.LEASE_TOKEN)
            .setNull(D.LEASE_UNTIL)
            .where(inboxPushFence(companyId, inboxId, owner, token))
            .execute() == 1

    override fun exhausted(
        olderThan: Instant,
        maximumAttempts: Int,
        limit: Int,
    ): List<InboxPushDispatchesRecord> {
        val unleased =
            D.STATE.eq("PENDING")
                .or(
                    D.STATE.eq("LEASED")
                        .and(
                            D.LEASE_UNTIL.le(
                                DSL.field(
                                    "statement_timestamp()",
                                    java.time.OffsetDateTime::class.java,
                                )
                            )
                        )
                )
        // Separate indexed ranges bound candidate scanning even while the live queue is large.
        val expired =
            sql.select(D.COMPANY_ID, D.INBOX_ID)
                .from(D)
                .where(
                    unleased,
                    D.STATE.`in`("PENDING", "LEASED"),
                    D.ENQUEUED_AT.le(olderThan.atOffset(UTC)),
                )
                .orderBy(D.ENQUEUED_AT, D.COMPANY_ID, D.INBOX_ID)
                .limit(limit)
        val abandoned =
            sql.select(D.COMPANY_ID, D.INBOX_ID)
                .from(D)
                .where(unleased, D.STATE.`in`("PENDING", "LEASED"), D.ATTEMPTS.ge(maximumAttempts))
                .orderBy(D.ATTEMPTS, D.ENQUEUED_AT, D.COMPANY_ID, D.INBOX_ID)
                .limit(limit)
        return sql.selectFrom(D)
            .where(DSL.row(D.COMPANY_ID, D.INBOX_ID).`in`(expired.union(abandoned)))
            .orderBy(D.ENQUEUED_AT, D.COMPANY_ID, D.INBOX_ID)
            .limit(limit)
            .forUpdate()
            .skipLocked()
            .fetch()
    }

    override fun failUnleased(
        companyId: UUID,
        inboxId: UUID,
        expectedProcessedCount: Int,
        expectedAttempts: Int,
        code: String,
        now: Instant,
    ): Boolean =
        sql.update(D)
            .set(D.STATE, "FAILED")
            .set(D.FINISHED_AT, now.atOffset(UTC))
            .set(D.FAILURE_CODE, code)
            .setNull(D.TARGET_SESSION_ID)
            .setNull(D.LEASE_OWNER)
            .setNull(D.LEASE_TOKEN)
            .setNull(D.LEASE_UNTIL)
            .where(D.COMPANY_ID.eq(companyId), D.INBOX_ID.eq(inboxId))
            .and(D.PROCESSED_COUNT.eq(expectedProcessedCount))
            .and(D.ATTEMPTS.eq(expectedAttempts))
            .and(
                D.STATE.eq("PENDING")
                    .or(
                        D.STATE.eq("LEASED")
                            .and(
                                D.LEASE_UNTIL.le(
                                    DSL.field(
                                        "clock_timestamp()",
                                        java.time.OffsetDateTime::class.java,
                                    )
                                )
                            )
                    )
            )
            .execute() == 1

    override fun purgeFinished(before: Instant, limit: Int): Int =
        sql.deleteFrom(D)
            .where(
                DSL.row(D.COMPANY_ID, D.INBOX_ID)
                    .`in`(
                        sql.select(D.COMPANY_ID, D.INBOX_ID)
                            .from(D)
                            .where(D.STATE.`in`("COMPLETE", "FAILED", "SUPERSEDED"))
                            .and(D.FINISHED_AT.lt(before.atOffset(UTC)))
                            .orderBy(D.FINISHED_AT, D.COMPANY_ID, D.INBOX_ID)
                            .limit(limit)
                            .forUpdate()
                            .skipLocked()
                    )
            )
            .execute()
}
