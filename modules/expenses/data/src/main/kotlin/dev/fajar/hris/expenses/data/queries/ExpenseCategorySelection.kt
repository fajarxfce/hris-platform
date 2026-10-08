package dev.fajar.hris.expenses.data.queries

import dev.fajar.hris.expenses.data.models.ExpenseCategoryRow
import dev.fajar.hris.schema.tables.ExpenseCategories.EXPENSE_CATEGORIES as C
import dev.fajar.hris.schema.tables.ExpenseCategoryRevisions.EXPENSE_CATEGORY_REVISIONS as R
import org.jooq.DSLContext
import org.jooq.Record

fun selectExpenseCategories(sql: DSLContext) =
    sql.select(C.CODE, C.VERSION, *R.fields())
        .from(C)
        .join(R)
        .on(R.COMPANY_ID.eq(C.COMPANY_ID).and(R.CATEGORY_ID.eq(C.ID)))

fun Record.toExpenseCategoryRow() =
    ExpenseCategoryRow(requireNotNull(get(C.CODE)), requireNotNull(get(C.VERSION)), into(R))
