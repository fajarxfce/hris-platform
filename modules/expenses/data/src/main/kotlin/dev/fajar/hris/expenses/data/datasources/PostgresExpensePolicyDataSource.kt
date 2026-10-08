package dev.fajar.hris.expenses.data.datasources

import dev.fajar.hris.expenses.data.models.ExpenseCategoryRow
import dev.fajar.hris.expenses.data.queries.*
import dev.fajar.hris.schema.tables.ExpenseCategories.EXPENSE_CATEGORIES as C
import dev.fajar.hris.schema.tables.ExpenseCategoryRevisions.EXPENSE_CATEGORY_REVISIONS as R
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresExpensePolicyDataSource(private val sql: DSLContext) : ExpensePolicyDataSource {
    override fun lock(company: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "expenses:$company")
            .execute()
    }

    override fun count(company: UUID): Int = sql.fetchCount(C, C.COMPANY_ID.eq(company))

    override fun find(company: UUID, id: UUID): ExpenseCategoryRow? =
        selectExpenseCategories(sql)
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.eq(id))
            .and(R.REVISION.eq(C.VERSION))
            .fetchOne { it.toExpenseCategoryRow() }

    override fun effective(company: UUID, id: UUID, asOf: LocalDate): ExpenseCategoryRow? =
        selectExpenseCategories(sql)
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.eq(id))
            .and(R.EFFECTIVE_FROM.le(asOf))
            .orderBy(R.EFFECTIVE_FROM.desc(), R.REVISION.desc())
            .limit(1)
            .fetchOne { it.toExpenseCategoryRow() }

    override fun list(
        company: UUID,
        asOf: LocalDate,
        after: String?,
        limit: Int,
    ): List<ExpenseCategoryRow> =
        sql.select(C.CODE, C.VERSION, *R.fields())
            .distinctOn(C.CODE)
            .from(C)
            .join(R)
            .on(R.COMPANY_ID.eq(C.COMPANY_ID).and(R.CATEGORY_ID.eq(C.ID)))
            .where(C.COMPANY_ID.eq(company))
            .and(R.EFFECTIVE_FROM.le(asOf))
            .and(after?.let { C.CODE.gt(it) } ?: DSL.noCondition())
            .orderBy(C.CODE, R.EFFECTIVE_FROM.desc(), R.REVISION.desc())
            .limit(limit)
            .fetch { it.toExpenseCategoryRow() }

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<ExpenseCategoryRow> =
        selectExpenseCategories(sql)
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.eq(id))
            .and(after?.let { R.REVISION.gt(it) } ?: DSL.noCondition())
            .orderBy(R.REVISION)
            .limit(limit)
            .fetch { it.toExpenseCategoryRow() }

    override fun insert(row: ExpenseCategoriesRecord) {
        sql.insertInto(C).set(row).execute()
    }

    override fun advance(company: UUID, id: UUID, expectedVersion: Long): Long? =
        sql.update(C)
            .set(C.VERSION, expectedVersion + 1)
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.eq(id))
            .and(C.VERSION.eq(expectedVersion))
            .returning(C.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: ExpenseCategoryRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
