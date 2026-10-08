package dev.fajar.hris.expenses.data.datasources

import dev.fajar.hris.expenses.data.models.ExpenseCategoryRow
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface ExpensePolicyDataSource {
    fun lock(company: UUID)

    fun count(company: UUID): Int

    fun find(company: UUID, id: UUID): ExpenseCategoryRow?

    fun effective(company: UUID, id: UUID, asOf: LocalDate): ExpenseCategoryRow?

    fun list(company: UUID, asOf: LocalDate, after: String?, limit: Int): List<ExpenseCategoryRow>

    fun history(company: UUID, id: UUID, after: Long?, limit: Int): List<ExpenseCategoryRow>

    fun insert(row: ExpenseCategoriesRecord)

    fun advance(company: UUID, id: UUID, expectedVersion: Long): Long?

    fun append(row: ExpenseCategoryRevisionsRecord)
}
