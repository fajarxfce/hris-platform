package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.Tables.EMPLOYEE_IMPORTS as B
import dev.fajar.hris.schema.Tables.EMPLOYEE_IMPORT_ATTEMPTS as A
import dev.fajar.hris.schema.Tables.EMPLOYEE_IMPORT_ROWS as R
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL

class PostgresEmployeeImportDataSource(private val sql: DSLContext) : EmployeeImportDataSource {
    override fun lock(companyId: UUID, id: UUID, shared: Boolean) {
        val query = sql.select(B.ID).from(B).where(B.COMPANY_ID.eq(companyId)).and(B.ID.eq(id))
        if (shared) query.forShare().fetch() else query.forUpdate().fetch()
    }

    override fun insertBatch(record: EmployeeImportsRecord) {
        sql.insertInto(B).set(record).execute()
    }

    override fun insertRows(records: List<EmployeeImportRowsRecord>) {
        sql.batch(records.map { sql.insertInto(R).set(it) }).execute()
    }

    override fun insertAttempt(record: EmployeeImportAttemptsRecord) {
        sql.insertInto(A).set(record).execute()
    }

    override fun find(companyId: UUID, id: UUID, lock: Boolean): EmployeeImportsRecord? {
        val query = sql.selectFrom(B).where(B.COMPANY_ID.eq(companyId)).and(B.ID.eq(id))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun forJob(companyId: UUID, jobId: UUID, lock: Boolean): EmployeeImportsRecord? {
        val query = sql.selectFrom(B).where(B.COMPANY_ID.eq(companyId)).and(B.JOB_ID.eq(jobId))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun list(companyId: UUID, after: UUID?, limit: Int) =
        sql.selectFrom(B)
            .where(B.COMPANY_ID.eq(companyId))
            .and(after?.let { B.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(B.ID)
            .limit(limit)
            .fetch()

    override fun rows(companyId: UUID, id: UUID, after: Int?, limit: Int) =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.IMPORT_ID.eq(id))
            .and(after?.let { R.ROW_NUMBER.gt(it) } ?: DSL.noCondition())
            .orderBy(R.ROW_NUMBER)
            .limit(limit)
            .fetch()

    override fun nextRow(companyId: UUID, id: UUID, status: String, after: Int) =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.IMPORT_ID.eq(id))
            .and(R.STATUS.eq(status))
            .and(R.ROW_NUMBER.gt(after))
            .orderBy(R.ROW_NUMBER)
            .limit(1)
            .fetchOne()

    override fun counts(companyId: UUID, id: UUID) =
        sql.select(R.STATUS, DSL.count())
            .from(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.IMPORT_ID.eq(id))
            .groupBy(R.STATUS)
            .fetch()
            .associate { requireNotNull(it.value1()) to requireNotNull(it.value2()) }

    override fun outcome(
        companyId: UUID,
        id: UUID,
        rowNumber: Int,
        expectedStatus: String,
        status: String,
        issues: JSONB,
        employeeId: UUID?,
    ) =
        sql.update(R)
            .set(R.STATUS, status)
            .set(R.ISSUES, issues)
            .set(R.CREATED_EMPLOYMENT_ID, employeeId)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.IMPORT_ID.eq(id))
            .and(R.ROW_NUMBER.eq(rowNumber))
            .and(R.STATUS.eq(expectedStatus))
            .execute()

    override fun transition(
        companyId: UUID,
        id: UUID,
        version: Long,
        status: String,
        jobId: UUID,
    ): Long? =
        sql.update(B)
            .set(B.STATUS, status)
            .set(B.JOB_ID, jobId)
            .set(B.VERSION, version + 1)
            .where(B.COMPANY_ID.eq(companyId))
            .and(B.ID.eq(id))
            .and(B.VERSION.eq(version))
            .returning(B.VERSION)
            .fetchOne()
            ?.version

    override fun attempts(companyId: UUID, id: UUID, after: UUID?, limit: Int) =
        sql.selectFrom(A)
            .where(A.COMPANY_ID.eq(companyId))
            .and(A.IMPORT_ID.eq(id))
            .and(after?.let { A.JOB_ID.gt(it) } ?: DSL.noCondition())
            .orderBy(A.JOB_ID)
            .limit(limit)
            .fetch()
}
