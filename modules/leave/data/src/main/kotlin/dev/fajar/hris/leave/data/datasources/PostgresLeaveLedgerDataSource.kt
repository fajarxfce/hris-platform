package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveBalanceSummaryRow
import dev.fajar.hris.schema.tables.LeaveAccounts.LEAVE_ACCOUNTS as A
import dev.fajar.hris.schema.tables.LeaveLedger.LEAVE_LEDGER as L
import dev.fajar.hris.schema.tables.LeaveTypeRevisions.LEAVE_TYPE_REVISIONS as R
import dev.fajar.hris.schema.tables.LeaveTypes.LEAVE_TYPES as T
import dev.fajar.hris.schema.tables.records.LeaveAccountsRecord
import dev.fajar.hris.schema.tables.records.LeaveLedgerRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLeaveLedgerDataSource(private val sql: DSLContext) : LeaveLedgerDataSource {
    override fun lock(company: UUID, employee: UUID, shared: Boolean) {
        val statement =
            if (shared) "select pg_advisory_xact_lock_shared(hashtextextended(?,0))"
            else "select pg_advisory_xact_lock(hashtextextended(?,0))"
        sql.query(statement, "leave:$company:$employee").execute()
    }

    override fun account(company: UUID, id: UUID): LeaveAccountsRecord? =
        sql.selectFrom(A).where(A.COMPANY_ID.eq(company)).and(A.ID.eq(id)).fetchOne()

    override fun balance(
        company: UUID,
        employee: UUID,
        type: UUID,
        year: Int,
    ): LeaveAccountsRecord? =
        sql.selectFrom(A)
            .where(A.COMPANY_ID.eq(company))
            .and(A.EMPLOYMENT_ID.eq(employee))
            .and(A.TYPE_ID.eq(type))
            .and(A.BALANCE_YEAR.eq(year))
            .fetchOne()

    override fun list(
        company: UUID,
        employee: UUID,
        year: Int,
        after: String?,
        limit: Int,
    ): List<LeaveBalanceSummaryRow> {
        val name = DSL.field("{0}->>'name'", String::class.java, R.DETAILS)
        return sql.select(A.fields().toList() + listOf(T.CODE, name))
            .from(A)
            .join(T)
            .on(T.COMPANY_ID.eq(A.COMPANY_ID).and(T.ID.eq(A.TYPE_ID)))
            .join(R)
            .on(R.COMPANY_ID.eq(T.COMPANY_ID).and(R.TYPE_ID.eq(T.ID)).and(R.REVISION.eq(T.VERSION)))
            .where(A.COMPANY_ID.eq(company))
            .and(A.EMPLOYMENT_ID.eq(employee))
            .and(A.BALANCE_YEAR.eq(year))
            .and(after?.let { T.CODE.gt(it) } ?: DSL.noCondition())
            .orderBy(T.CODE)
            .limit(limit)
            .fetch { LeaveBalanceSummaryRow(it.into(A), it[T.CODE], it[name]) }
    }

    override fun entries(
        company: UUID,
        employee: UUID,
        type: UUID,
        year: Int,
        after: UUID?,
        limit: Int,
    ): List<LeaveLedgerRecord> {
        val cursor = L.`as`("cursor")
        val before =
            if (after == null) DSL.noCondition()
            else
                DSL.row(L.RECORDED_AT, L.ID)
                    .lt(
                        DSL.select(cursor.RECORDED_AT, cursor.ID)
                            .from(cursor)
                            .where(cursor.COMPANY_ID.eq(company))
                            .and(cursor.EMPLOYMENT_ID.eq(employee))
                            .and(cursor.TYPE_ID.eq(type))
                            .and(cursor.BALANCE_YEAR.eq(year))
                            .and(cursor.ID.eq(after))
                    )
        return sql.selectFrom(L)
            .where(L.COMPANY_ID.eq(company))
            .and(L.EMPLOYMENT_ID.eq(employee))
            .and(L.TYPE_ID.eq(type))
            .and(L.BALANCE_YEAR.eq(year))
            .and(before)
            .orderBy(L.RECORDED_AT.desc(), L.ID.desc())
            .limit(limit)
            .fetch()
    }

    override fun append(rows: List<LeaveLedgerRecord>) {
        sql.batchInsert(rows).execute()
    }
}
