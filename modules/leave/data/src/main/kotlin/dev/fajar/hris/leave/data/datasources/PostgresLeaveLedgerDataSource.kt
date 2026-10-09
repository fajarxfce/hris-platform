package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.schema.tables.LeaveAccounts.LEAVE_ACCOUNTS as A
import dev.fajar.hris.schema.tables.LeaveLedger.LEAVE_LEDGER as L
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
