package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveTypeRow
import dev.fajar.hris.leave.data.queries.*
import dev.fajar.hris.schema.tables.LeaveTypeRevisions.LEAVE_TYPE_REVISIONS as R
import dev.fajar.hris.schema.tables.LeaveTypes.LEAVE_TYPES as T
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLeavePolicyDataSource(private val sql: DSLContext) : LeavePolicyDataSource {
    override fun lock(company: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "leave-policy:$company")
            .execute()
    }

    override fun find(company: UUID, id: UUID): LeaveTypeRow? =
        selectLeaveTypes(sql)
            .where(T.COMPANY_ID.eq(company))
            .and(T.ID.eq(id))
            .and(R.REVISION.eq(T.VERSION))
            .fetchOne { it.toLeaveTypeRow() }

    override fun effective(company: UUID, id: UUID, asOf: LocalDate): LeaveTypeRow? =
        selectLeaveTypes(sql)
            .where(T.COMPANY_ID.eq(company))
            .and(T.ID.eq(id))
            .and(R.EFFECTIVE_FROM.le(asOf))
            .orderBy(T.CODE, R.EFFECTIVE_FROM.desc(), R.REVISION.desc())
            .fetchOne { it.toLeaveTypeRow() }

    override fun list(
        company: UUID,
        asOf: LocalDate,
        after: String?,
        limit: Int,
    ): List<LeaveTypeRow> =
        selectLeaveTypes(sql)
            .where(T.COMPANY_ID.eq(company))
            .and(R.EFFECTIVE_FROM.le(asOf))
            .and(after?.let { T.CODE.gt(it) } ?: DSL.noCondition())
            .orderBy(T.CODE, R.EFFECTIVE_FROM.desc(), R.REVISION.desc())
            .limit(limit)
            .fetch { it.toLeaveTypeRow() }

    override fun insert(row: LeaveTypesRecord) {
        sql.insertInto(T).set(row).execute()
    }

    override fun advanceVersion(company: UUID, id: UUID, expectedVersion: Long): Long? =
        sql.update(T)
            .set(T.VERSION, expectedVersion + 1)
            .where(T.COMPANY_ID.eq(company))
            .and(T.ID.eq(id))
            .and(T.VERSION.eq(expectedVersion))
            .returning(T.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: LeaveTypeRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
