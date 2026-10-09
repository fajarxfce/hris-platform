package dev.fajar.hris.jobs.data.datasources

import dev.fajar.hris.schema.tables.BackgroundJobs.BACKGROUND_JOBS as J
import dev.fajar.hris.schema.tables.records.BackgroundJobsRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL

class PostgresJobDataSource(private val sql: DSLContext) : JobDataSource {
    override fun lockQueue(companyId: UUID) {
        sql.select(
                DSL.field(
                    "pg_advisory_xact_lock(hashtextextended({0},0))",
                    String::class.java,
                    DSL.`val`("hris:job-queue:$companyId"),
                )
            )
            .fetch()
    }

    override fun pendingCount(companyId: UUID): Int =
        sql.selectCount()
            .from(J)
            .where(J.COMPANY_ID.eq(companyId))
            .and(J.STATUS.`in`("QUEUED", "RUNNING"))
            .fetchOne(0, Int::class.java) ?: 0

    override fun insert(record: BackgroundJobsRecord): BackgroundJobsRecord =
        sql.insertInto(J).set(record).returning().fetchSingle()

    override fun find(companyId: UUID, id: UUID, lock: Boolean): BackgroundJobsRecord? {
        val query = sql.selectFrom(J).where(J.ID.eq(id)).and(J.COMPANY_ID.eq(companyId))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun list(
        companyId: UUID,
        actorId: UUID?,
        size: Int,
        beforeAt: Instant?,
        beforeId: UUID?,
    ): List<BackgroundJobsRecord> {
        val query = sql.selectFrom(J).where(J.COMPANY_ID.eq(companyId))
        if (actorId != null) query.and(J.ACTOR_ID.eq(actorId))
        if (beforeAt != null && beforeId != null) {
            val at = OffsetDateTime.ofInstant(beforeAt, ZoneOffset.UTC)
            query.and(J.CREATED_AT.lt(at).or(J.CREATED_AT.eq(at).and(J.ID.gt(beforeId))))
        }
        return query.orderBy(J.CREATED_AT.desc(), J.ID).limit(size).fetch()
    }

    override fun requestCancellation(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        requestedAt: Instant,
    ): BackgroundJobsRecord? =
        sql.update(J)
            .set(J.CANCELLATION_REQUESTED, true)
            .set(
                J.AVAILABLE_AT,
                DSL.least(
                    J.AVAILABLE_AT,
                    DSL.`val`(OffsetDateTime.ofInstant(requestedAt, ZoneOffset.UTC)),
                ),
            )
            .set(J.VERSION, J.VERSION.plus(1))
            .where(J.ID.eq(id))
            .and(J.COMPANY_ID.eq(companyId))
            .and(J.VERSION.eq(expectedVersion))
            .and(J.STATUS.`in`("QUEUED", "RUNNING"))
            .and(J.CANCELLATION_REQUESTED.isFalse)
            .returning()
            .fetchOne()

    override fun claim(
        owner: UUID,
        limit: Int,
        seconds: Int,
        kinds: Set<String>,
        maximumAttempts: Int,
    ): List<BackgroundJobsRecord> =
        sql.selectFrom(
                DSL.table(
                    "claim_background_jobs({0},{1},{2},{3},{4})",
                    DSL.`val`(owner),
                    DSL.`val`(limit),
                    DSL.`val`(seconds),
                    DSL.`val`(kinds.toTypedArray()),
                    DSL.`val`(maximumAttempts),
                )
            )
            .fetchInto(BackgroundJobsRecord::class.java)

    override fun exhausted(
        kinds: Set<String>,
        maximumAttempts: Int,
        limit: Int,
    ): List<BackgroundJobsRecord> =
        sql.selectFrom(J)
            .where(J.STATUS.eq("RUNNING"))
            .and(J.LEASE_UNTIL.le(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .and(J.ATTEMPTS.ge(maximumAttempts))
            .and(J.KIND.`in`(kinds))
            .orderBy(J.LEASE_UNTIL, J.ID)
            .limit(limit)
            .fetch()

    override fun failExpired(
        id: UUID,
        companyId: UUID,
        token: UUID,
        version: Long,
        failureCode: String,
    ): Boolean =
        sql.update(J)
            .set(J.STATUS, "FAILED")
            .set(J.FAILURE_CODE, failureCode)
            .set(J.FINISHED_AT, DSL.field("clock_timestamp()", OffsetDateTime::class.java))
            .setNull(J.LEASE_OWNER)
            .setNull(J.LEASE_TOKEN)
            .setNull(J.LEASE_UNTIL)
            .set(J.VERSION, J.VERSION.plus(1))
            .where(J.ID.eq(id))
            .and(J.COMPANY_ID.eq(companyId))
            .and(J.LEASE_TOKEN.eq(token))
            .and(J.VERSION.eq(version))
            .and(J.STATUS.eq("RUNNING"))
            .and(J.LEASE_UNTIL.le(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .execute() == 1

    override fun renew(id: UUID, owner: UUID, token: UUID, seconds: Int): Boolean =
        sql.update(J)
            .set(
                J.LEASE_UNTIL,
                DSL.field(
                    "clock_timestamp()+make_interval(secs=>{0})",
                    OffsetDateTime::class.java,
                    DSL.`val`(seconds),
                ),
            )
            .where(J.ID.eq(id))
            .and(J.LEASE_OWNER.eq(owner))
            .and(J.LEASE_TOKEN.eq(token))
            .and(J.STATUS.eq("RUNNING"))
            .and(J.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .execute() == 1

    override fun lockLease(
        id: UUID,
        companyId: UUID,
        owner: UUID,
        token: UUID,
    ): BackgroundJobsRecord? =
        sql.selectFrom(J)
            .where(J.ID.eq(id))
            .and(J.COMPANY_ID.eq(companyId))
            .and(J.LEASE_OWNER.eq(owner))
            .and(J.LEASE_TOKEN.eq(token))
            .and(J.STATUS.eq("RUNNING"))
            .and(J.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .forUpdate()
            .fetchOne()

    override fun checkpoint(
        id: UUID,
        companyId: UUID,
        owner: UUID,
        token: UUID,
        completed: Int,
        checkpoint: JSONB,
    ): Boolean =
        sql.update(J)
            .set(J.COMPLETED_ITEMS, completed)
            .set(J.CHECKPOINT, checkpoint)
            .set(J.VERSION, J.VERSION.plus(1))
            .where(J.ID.eq(id))
            .and(J.COMPANY_ID.eq(companyId))
            .and(J.LEASE_OWNER.eq(owner))
            .and(J.LEASE_TOKEN.eq(token))
            .and(J.STATUS.eq("RUNNING"))
            .and(J.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .and(J.CANCELLATION_REQUESTED.isFalse)
            .and(J.COMPLETED_ITEMS.le(completed))
            .and(J.TOTAL_ITEMS.ge(completed))
            .and(J.COMPLETED_ITEMS.lt(completed).or(J.CHECKPOINT.eq(checkpoint)))
            .execute() == 1

    override fun complete(
        id: UUID,
        companyId: UUID,
        owner: UUID,
        token: UUID,
        status: String,
        failureCode: String?,
    ): Boolean {
        val query =
            sql.update(J)
                .set(J.STATUS, status)
                .set(J.FAILURE_CODE, failureCode)
                .set(J.FINISHED_AT, DSL.field("clock_timestamp()", OffsetDateTime::class.java))
                .set(J.VERSION, J.VERSION.plus(1))
                .setNull(J.LEASE_OWNER)
                .setNull(J.LEASE_TOKEN)
                .setNull(J.LEASE_UNTIL)
                .where(J.ID.eq(id))
                .and(J.COMPANY_ID.eq(companyId))
                .and(J.LEASE_OWNER.eq(owner))
                .and(J.LEASE_TOKEN.eq(token))
                .and(J.STATUS.eq("RUNNING"))
                .and(J.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
        if (status == "SUCCEEDED")
            query
                .and(J.CANCELLATION_REQUESTED.isFalse)
                .and(J.PROGRESS_MODE.eq("UPPER_BOUND").or(J.COMPLETED_ITEMS.eq(J.TOTAL_ITEMS)))
        return query.execute() == 1
    }

    override fun defer(
        id: UUID,
        companyId: UUID,
        owner: UUID,
        token: UUID,
        seconds: Long,
        failureCode: String,
    ): Boolean =
        sql.update(J)
            .set(J.STATUS, "QUEUED")
            .set(J.FAILURE_CODE, failureCode)
            .set(J.VERSION, J.VERSION.plus(1))
            .set(
                J.AVAILABLE_AT,
                DSL.field(
                    "clock_timestamp()+make_interval(secs=>{0})",
                    OffsetDateTime::class.java,
                    DSL.`val`(seconds),
                ),
            )
            .setNull(J.LEASE_OWNER)
            .setNull(J.LEASE_TOKEN)
            .setNull(J.LEASE_UNTIL)
            .where(J.ID.eq(id))
            .and(J.COMPANY_ID.eq(companyId))
            .and(J.LEASE_OWNER.eq(owner))
            .and(J.LEASE_TOKEN.eq(token))
            .and(J.STATUS.eq("RUNNING"))
            .and(J.ATTEMPTS.lt(8))
            .and(J.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
            .execute() == 1
}
