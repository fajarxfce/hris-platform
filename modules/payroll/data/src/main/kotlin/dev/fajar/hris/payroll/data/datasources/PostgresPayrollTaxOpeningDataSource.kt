package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollTaxOpeningRow
import dev.fajar.hris.schema.tables.PayrollTaxOpeningRevisions.PAYROLL_TAX_OPENING_REVISIONS as R
import dev.fajar.hris.schema.tables.PayrollTaxOpenings.PAYROLL_TAX_OPENINGS as H
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollTaxOpeningDataSource(private val sql: DSLContext) :
    PayrollTaxOpeningDataSource {
    override fun revision(
        company: UUID,
        employee: UUID,
        year: Int,
        revision: Long,
    ): PayrollTaxOpeningRow? =
        sql.select(H.EMPLOYMENT_ID, H.TAX_YEAR, *R.fields())
            .from(H)
            .join(R)
            .on(R.COMPANY_ID.eq(H.COMPANY_ID).and(R.OPENING_ID.eq(H.ID)))
            .where(
                H.COMPANY_ID.eq(company),
                H.EMPLOYMENT_ID.eq(employee),
                H.TAX_YEAR.eq(year),
                R.REVISION.eq(revision),
            )
            .fetchOne {
                PayrollTaxOpeningRow(it.get(H.EMPLOYMENT_ID)!!, it.get(H.TAX_YEAR)!!, it.into(R))
            }

    override fun find(company: UUID, employee: UUID, year: Int): PayrollTaxOpeningRow? =
        sql.select(H.EMPLOYMENT_ID, H.TAX_YEAR, *R.fields())
            .from(H)
            .join(R)
            .on(
                R.COMPANY_ID.eq(H.COMPANY_ID)
                    .and(R.OPENING_ID.eq(H.ID))
                    .and(R.REVISION.eq(H.VERSION))
            )
            .where(H.COMPANY_ID.eq(company), H.EMPLOYMENT_ID.eq(employee), H.TAX_YEAR.eq(year))
            .fetchOne {
                PayrollTaxOpeningRow(it.get(H.EMPLOYMENT_ID)!!, it.get(H.TAX_YEAR)!!, it.into(R))
            }

    override fun history(
        company: UUID,
        employee: UUID,
        year: Int,
        after: Long?,
        limit: Int,
    ): List<PayrollTaxOpeningRow> =
        sql.select(H.EMPLOYMENT_ID, H.TAX_YEAR, *R.fields())
            .from(H)
            .join(R)
            .on(R.COMPANY_ID.eq(H.COMPANY_ID).and(R.OPENING_ID.eq(H.ID)))
            .where(
                H.COMPANY_ID.eq(company),
                H.EMPLOYMENT_ID.eq(employee),
                H.TAX_YEAR.eq(year),
                after?.let { R.REVISION.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(R.REVISION)
            .limit(limit)
            .fetch {
                PayrollTaxOpeningRow(it.get(H.EMPLOYMENT_ID)!!, it.get(H.TAX_YEAR)!!, it.into(R))
            }

    override fun list(
        company: UUID,
        year: Int,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<PayrollTaxOpeningRow> =
        sql.select(H.EMPLOYMENT_ID, H.TAX_YEAR, *R.fields())
            .from(H)
            .join(R)
            .on(
                R.COMPANY_ID.eq(H.COMPANY_ID)
                    .and(R.OPENING_ID.eq(H.ID))
                    .and(R.REVISION.eq(H.VERSION))
            )
            .where(
                H.COMPANY_ID.eq(company),
                H.TAX_YEAR.eq(year),
                status?.let { R.STATUS.eq(it) } ?: DSL.noCondition(),
                after?.let { H.EMPLOYMENT_ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(H.EMPLOYMENT_ID)
            .limit(limit)
            .fetch {
                PayrollTaxOpeningRow(it.get(H.EMPLOYMENT_ID)!!, it.get(H.TAX_YEAR)!!, it.into(R))
            }

    override fun authors(company: UUID, id: UUID): List<UUID> =
        sql.selectDistinct(R.PREPARED_BY)
            .from(R)
            .where(R.COMPANY_ID.eq(company), R.OPENING_ID.eq(id), R.STATUS.eq("DRAFT"))
            .limit(1000)
            .fetch(R.PREPARED_BY)

    override fun insert(row: PayrollTaxOpeningsRecord) {
        sql.insertInto(H).set(row).execute()
    }

    override fun advance(company: UUID, id: UUID, version: Long): Long? =
        sql.update(H)
            .set(H.VERSION, version + 1)
            .where(H.COMPANY_ID.eq(company), H.ID.eq(id), H.VERSION.eq(version))
            .returning(H.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: PayrollTaxOpeningRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
