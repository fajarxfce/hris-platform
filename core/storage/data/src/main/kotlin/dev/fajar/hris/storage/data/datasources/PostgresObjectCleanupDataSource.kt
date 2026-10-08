package dev.fajar.hris.storage.data.datasources

import dev.fajar.hris.schema.Tables.OBJECT_CLEANUP_QUEUE as Q
import dev.fajar.hris.schema.tables.records.ObjectCleanupQueueRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresObjectCleanupDataSource(private val sql: DSLContext) : ObjectCleanupDataSource {
    override fun insert(row: ObjectCleanupQueueRecord) {
        sql.insertInto(Q).set(row).execute()
    }

    override fun bytes(companyId: UUID): Long =
        sql.select(DSL.coalesce(DSL.sum(Q.OBJECT_BYTES), java.math.BigDecimal.ZERO))
            .from(Q)
            .where(Q.COMPANY_ID.eq(companyId))
            .fetchOne(0, Long::class.java) ?: 0

    override fun retain(companyId: UUID, ids: Set<UUID>): Int =
        sql.deleteFrom(Q)
            .where(Q.COMPANY_ID.eq(companyId))
            .and(Q.ID.`in`(ids))
            .and(Q.STATUS.eq("PENDING"))
            .execute()

    override fun advance(companyId: UUID, resourceId: UUID, at: OffsetDateTime) {
        sql.update(Q)
            .set(Q.ELIGIBLE_AT, at)
            .set(Q.VERSION, Q.VERSION.plus(1))
            .where(Q.COMPANY_ID.eq(companyId))
            .and(Q.RESOURCE_ID.eq(resourceId))
            .and(Q.STATUS.eq("PENDING"))
            .and(Q.ELIGIBLE_AT.gt(at))
            .execute()
    }

    override fun claim(
        owner: UUID,
        limit: Int,
        seconds: Int,
        attempts: Int,
    ): List<ObjectCleanupQueueRecord> =
        sql.resultQuery(
                "select * from claim_object_cleanup(?::uuid,?,?,?)",
                owner,
                limit,
                seconds,
                attempts,
            )
            .fetchInto(Q)

    override fun exhausted(attempts: Int, limit: Int): List<ObjectCleanupQueueRecord> =
        sql.selectFrom(Q)
            .where(Q.STATUS.eq("RUNNING"))
            .and(Q.ATTEMPTS.ge(attempts))
            .and(Q.LEASE_UNTIL.le(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .orderBy(Q.LEASE_UNTIL, Q.ID)
            .limit(limit)
            .fetch()

    override fun failExpired(id: UUID, token: UUID): Int =
        sql.update(Q)
            .set(Q.STATUS, "FAILED")
            .set(Q.FAILURE_CODE, "cleanup_attempts_exhausted")
            .setNull(Q.LEASE_TOKEN)
            .setNull(Q.LEASE_OWNER)
            .setNull(Q.LEASE_UNTIL)
            .set(Q.VERSION, Q.VERSION.plus(1))
            .where(Q.ID.eq(id))
            .and(Q.STATUS.eq("RUNNING"))
            .and(Q.LEASE_TOKEN.eq(token))
            .and(Q.LEASE_UNTIL.le(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .execute()

    override fun complete(id: UUID, token: UUID): Int =
        sql.deleteFrom(Q)
            .where(Q.ID.eq(id))
            .and(Q.STATUS.eq("RUNNING"))
            .and(Q.LEASE_TOKEN.eq(token))
            .and(Q.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .execute()

    override fun fail(
        id: UUID,
        token: UUID,
        code: String,
        status: String,
        at: OffsetDateTime,
    ): Int =
        sql.update(Q)
            .set(Q.STATUS, status)
            .set(Q.FAILURE_CODE, code)
            .set(Q.ELIGIBLE_AT, at)
            .setNull(Q.LEASE_TOKEN)
            .setNull(Q.LEASE_OWNER)
            .setNull(Q.LEASE_UNTIL)
            .set(Q.VERSION, Q.VERSION.plus(1))
            .where(Q.ID.eq(id))
            .and(Q.STATUS.eq("RUNNING"))
            .and(Q.LEASE_TOKEN.eq(token))
            .and(Q.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .execute()

    override fun find(companyId: UUID, id: UUID): ObjectCleanupQueueRecord? =
        sql.selectFrom(Q).where(Q.COMPANY_ID.eq(companyId)).and(Q.ID.eq(id)).fetchOne()

    override fun list(companyId: UUID, after: UUID?, limit: Int): List<ObjectCleanupQueueRecord> =
        sql.selectFrom(Q)
            .where(Q.COMPANY_ID.eq(companyId))
            .and(after?.let { Q.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(Q.ID)
            .limit(limit)
            .fetch()

    override fun retry(companyId: UUID, id: UUID, version: Long, at: OffsetDateTime): Long? =
        sql.update(Q)
            .set(Q.STATUS, "PENDING")
            .set(Q.ATTEMPTS, 0)
            .setNull(Q.FAILURE_CODE)
            .set(Q.ELIGIBLE_AT, at)
            .set(Q.VERSION, version + 1)
            .where(Q.COMPANY_ID.eq(companyId))
            .and(Q.ID.eq(id))
            .and(Q.VERSION.eq(version))
            .and(Q.STATUS.eq("FAILED"))
            .returning(Q.VERSION)
            .fetchOne()
            ?.version
}
