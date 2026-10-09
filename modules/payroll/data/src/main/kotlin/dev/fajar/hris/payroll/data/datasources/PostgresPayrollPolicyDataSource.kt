package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollPolicyRow
import dev.fajar.hris.schema.tables.PayrollPolicies.PAYROLL_POLICIES as P
import dev.fajar.hris.schema.tables.PayrollPolicyRevisions.PAYROLL_POLICY_REVISIONS as R
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollPolicyDataSource(private val sql: DSLContext) : PayrollPolicyDataSource {
    override fun lock(company: UUID, shared: Boolean) {
        val query =
            if (shared) "select pg_advisory_xact_lock_shared(hashtextextended(?,0))"
            else "select pg_advisory_xact_lock(hashtextextended(?,0))"
        sql.query(query, "payroll:$company").execute()
    }

    override fun current(company: UUID): PayrollPolicyRow? =
        sql.select(P.VERSION, *R.fields())
            .from(P)
            .join(R)
            .on(R.COMPANY_ID.eq(P.COMPANY_ID).and(R.REVISION.eq(P.VERSION)))
            .where(P.COMPANY_ID.eq(company))
            .fetchOne { PayrollPolicyRow(it.get(P.VERSION)!!, it.into(R)) }

    override fun effective(company: UUID, month: LocalDate): PayrollPolicyRow? =
        sql.select(P.VERSION, *R.fields())
            .from(P)
            .join(R)
            .on(R.COMPANY_ID.eq(P.COMPANY_ID))
            .where(P.COMPANY_ID.eq(company))
            .and(R.EFFECTIVE_FROM.le(month))
            .orderBy(R.EFFECTIVE_FROM.desc(), R.REVISION.desc())
            .limit(1)
            .fetchOne { PayrollPolicyRow(it.get(P.VERSION)!!, it.into(R)) }

    override fun history(company: UUID, after: Long?, limit: Int): List<PayrollPolicyRow> =
        sql.select(P.VERSION, *R.fields())
            .from(P)
            .join(R)
            .on(R.COMPANY_ID.eq(P.COMPANY_ID))
            .where(P.COMPANY_ID.eq(company))
            .and(after?.let { R.REVISION.gt(it) } ?: DSL.noCondition())
            .orderBy(R.REVISION)
            .limit(limit)
            .fetch { PayrollPolicyRow(it.get(P.VERSION)!!, it.into(R)) }

    override fun insert(row: PayrollPoliciesRecord) {
        sql.insertInto(P).set(row).execute()
    }

    override fun advance(company: UUID, expectedVersion: Long): Long? =
        sql.update(P)
            .set(P.VERSION, expectedVersion + 1)
            .where(P.COMPANY_ID.eq(company))
            .and(P.VERSION.eq(expectedVersion))
            .returning(P.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: PayrollPolicyRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
