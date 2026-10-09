package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.schema.tables.LeaveAccounts.LEAVE_ACCOUNTS as A
import dev.fajar.hris.schema.tables.LeaveAccrualPostings.LEAVE_ACCRUAL_POSTINGS as P
import dev.fajar.hris.schema.tables.LeaveAccrualYears.LEAVE_ACCRUAL_YEARS as Y
import dev.fajar.hris.schema.tables.LeaveYearClosings.LEAVE_YEAR_CLOSINGS as C
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

class PostgresLeaveEntitlementDataSource(private val sql: DSLContext) : LeaveEntitlementDataSource {
    override fun frequency(company: UUID, employee: UUID, type: UUID, year: Int): String? =
        sql.select(Y.FREQUENCY)
            .from(Y)
            .where(
                Y.COMPANY_ID.eq(company),
                Y.EMPLOYMENT_ID.eq(employee),
                Y.TYPE_ID.eq(type),
                Y.BALANCE_YEAR.eq(year),
            )
            .fetchOne(Y.FREQUENCY)

    override fun ensureFrequency(
        company: UUID,
        employee: UUID,
        type: UUID,
        year: Int,
        frequency: String,
    ) {
        sql.insertInto(Y)
            .set(Y.COMPANY_ID, company)
            .set(Y.EMPLOYMENT_ID, employee)
            .set(Y.TYPE_ID, type)
            .set(Y.BALANCE_YEAR, year)
            .set(Y.FREQUENCY, frequency)
            .onConflict(Y.COMPANY_ID, Y.EMPLOYMENT_ID, Y.TYPE_ID, Y.BALANCE_YEAR)
            .doNothing()
            .execute()
    }

    override fun posting(
        company: UUID,
        employee: UUID,
        type: UUID,
        period: LocalDate,
    ): LeaveAccrualPostingsRecord? =
        sql.selectFrom(P)
            .where(
                P.COMPANY_ID.eq(company),
                P.EMPLOYMENT_ID.eq(employee),
                P.TYPE_ID.eq(type),
                P.PERIOD_KEY.eq(period),
            )
            .fetchOne()

    override fun postings(
        company: UUID,
        employee: UUID,
        type: UUID,
        year: Int,
    ): List<LeaveAccrualPostingsRecord> =
        sql.selectFrom(P)
            .where(
                P.COMPANY_ID.eq(company),
                P.EMPLOYMENT_ID.eq(employee),
                P.TYPE_ID.eq(type),
                P.BALANCE_YEAR.eq(year),
            )
            .orderBy(P.PERIOD_KEY)
            .limit(12)
            .fetch()

    override fun insertPosting(row: LeaveAccrualPostingsRecord) {
        sql.insertInto(P).set(row).execute()
    }

    override fun closing(
        company: UUID,
        employee: UUID,
        type: UUID,
        year: Int,
    ): LeaveYearClosingsRecord? =
        sql.selectFrom(C)
            .where(
                C.COMPANY_ID.eq(company),
                C.EMPLOYMENT_ID.eq(employee),
                C.TYPE_ID.eq(type),
                C.BALANCE_YEAR.eq(year),
            )
            .fetchOne()

    override fun insertClosing(row: LeaveYearClosingsRecord) {
        sql.insertInto(C).set(row).execute()
    }

    override fun ensureAccount(company: UUID, employee: UUID, type: UUID, year: Int) {
        sql.insertInto(A)
            .set(A.COMPANY_ID, company)
            .set(A.EMPLOYMENT_ID, employee)
            .set(A.TYPE_ID, type)
            .set(A.BALANCE_YEAR, year)
            .onConflict(A.COMPANY_ID, A.EMPLOYMENT_ID, A.TYPE_ID, A.BALANCE_YEAR)
            .doNothing()
            .execute()
    }

    override fun closeAccount(
        company: UUID,
        employee: UUID,
        type: UUID,
        year: Int,
        expectedVersion: Long,
        closingId: UUID,
    ): Long? =
        sql.update(A)
            .set(A.CLOSING_ID, closingId)
            .set(A.VERSION, expectedVersion + 1)
            .where(
                A.COMPANY_ID.eq(company),
                A.EMPLOYMENT_ID.eq(employee),
                A.TYPE_ID.eq(type),
                A.BALANCE_YEAR.eq(year),
                A.VERSION.eq(expectedVersion),
                A.CLOSING_ID.isNull,
            )
            .returning(A.VERSION)
            .fetchOne()
            ?.version
}
