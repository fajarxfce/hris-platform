package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveBalanceRow
import dev.fajar.hris.schema.tables.LeaveLedger.LEAVE_LEDGER as L
import dev.fajar.hris.schema.tables.records.LeaveLedgerRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLeaveLedgerDataSource(private val sql: DSLContext) : LeaveLedgerDataSource {
    override fun lock(company: UUID, employee: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "leave:$company:$employee")
            .execute()
    }

    override fun balance(company: UUID, employee: UUID, type: UUID, year: Int): LeaveBalanceRow {
        val row =
            sql.select(
                    DSL.sum(L.AVAILABLE_DELTA),
                    DSL.sum(L.RESERVED_DELTA),
                    DSL.sum(L.CONSUMED_DELTA),
                )
                .from(L)
                .where(L.COMPANY_ID.eq(company))
                .and(L.EMPLOYMENT_ID.eq(employee))
                .and(L.TYPE_ID.eq(type))
                .and(L.BALANCE_YEAR.eq(year))
                .fetchSingle()
        return LeaveBalanceRow(
            row.value1()?.longValueExact() ?: 0,
            row.value2()?.longValueExact() ?: 0,
            row.value3()?.longValueExact() ?: 0,
        )
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
