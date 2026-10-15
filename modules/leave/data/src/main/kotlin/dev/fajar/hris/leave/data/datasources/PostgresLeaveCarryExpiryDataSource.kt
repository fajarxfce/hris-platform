package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveCarryExpiryTargetRow
import dev.fajar.hris.schema.tables.LeaveCarryExpiries.LEAVE_CARRY_EXPIRIES as E
import dev.fajar.hris.schema.tables.LeaveCarryExpiryAttempts.LEAVE_CARRY_EXPIRY_ATTEMPTS as A
import dev.fajar.hris.schema.tables.LeaveCarryExpiryOutcomes.LEAVE_CARRY_EXPIRY_OUTCOMES as O
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLeaveCarryExpiryDataSource(private val sql: DSLContext) : LeaveCarryExpiryDataSource {
    override fun find(company: UUID, id: UUID, lock: Boolean): LeaveCarryExpiriesRecord? {
        val query = sql.selectFrom(E).where(E.COMPANY_ID.eq(company)).and(E.ID.eq(id))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun forJob(company: UUID, job: UUID, lock: Boolean): LeaveCarryExpiriesRecord? {
        val query = sql.selectFrom(E).where(E.COMPANY_ID.eq(company)).and(E.JOB_ID.eq(job))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun list(company: UUID, after: UUID?, limit: Int): List<LeaveCarryExpiriesRecord> =
        sql.selectFrom(E)
            .where(E.COMPANY_ID.eq(company))
            .and(if (after == null) DSL.noCondition() else E.ID.gt(after))
            .orderBy(E.ID)
            .limit(limit)
            .fetch()

    override fun insert(row: LeaveCarryExpiriesRecord) {
        sql.executeInsert(row)
    }

    override fun insertAttempt(row: LeaveCarryExpiryAttemptsRecord) {
        sql.executeInsert(row)
    }

    override fun next(company: UUID, id: UUID, after: Int): LeaveCarryExpiryTargetRow? =
        sql.fetchOne(
                """select e.company_id,e.id as expiry_id,t.ordinal,t.employment_id from leave_carry_expiries e
            join leave_batch_targets t on t.company_id=e.company_id and t.batch_id=e.batch_id
            where e.company_id=? and e.id=? and e.source_kind='BATCH' and t.ordinal>?
            union all
            select e.company_id,e.id as expiry_id,1 as ordinal,e.employment_id from leave_carry_expiries e
            where e.company_id=? and e.id=? and e.source_kind='CLOSING' and 1>?
            order by ordinal limit 1""",
                company,
                id,
                after,
                company,
                id,
                after,
            )
            ?.let {
                LeaveCarryExpiryTargetRow(
                    it.get("company_id", UUID::class.java)!!,
                    it.get("expiry_id", UUID::class.java)!!,
                    it.get("ordinal", Int::class.java)!!,
                    it.get("employment_id", UUID::class.java)!!,
                )
            }

    override fun completed(company: UUID, id: UUID): Int =
        sql.select(O.ORDINAL)
            .from(O)
            .where(O.COMPANY_ID.eq(company))
            .and(O.EXPIRY_ID.eq(id))
            .orderBy(O.ORDINAL.desc())
            .limit(1)
            .fetchOne(O.ORDINAL) ?: 0

    override fun outcome(row: LeaveCarryExpiryOutcomesRecord) {
        sql.executeInsert(row)
    }

    override fun results(
        company: UUID,
        id: UUID,
        after: Int?,
        limit: Int,
    ): List<LeaveCarryExpiryOutcomesRecord> =
        sql.selectFrom(O)
            .where(O.COMPANY_ID.eq(company))
            .and(O.EXPIRY_ID.eq(id))
            .and(if (after == null) DSL.noCondition() else O.ORDINAL.gt(after))
            .orderBy(O.ORDINAL)
            .limit(limit)
            .fetch()

    override fun attempts(company: UUID, id: UUID): List<LeaveCarryExpiryAttemptsRecord> =
        sql.selectFrom(A)
            .where(A.COMPANY_ID.eq(company))
            .and(A.EXPIRY_ID.eq(id))
            .orderBy(A.ATTEMPT)
            .limit(8)
            .fetch()

    override fun resume(company: UUID, id: UUID, version: Long, job: UUID): Boolean =
        sql.update(E)
            .set(E.JOB_ID, job)
            .set(E.VERSION, version + 1)
            .where(E.COMPANY_ID.eq(company))
            .and(E.ID.eq(id))
            .and(E.VERSION.eq(version))
            .execute() == 1
}
