package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollTaxOpeningRow
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface PayrollTaxOpeningDataSource {
    fun revision(company: UUID, employee: UUID, year: Int, revision: Long): PayrollTaxOpeningRow?

    fun find(company: UUID, employee: UUID, year: Int): PayrollTaxOpeningRow?

    fun history(
        company: UUID,
        employee: UUID,
        year: Int,
        after: Long?,
        limit: Int,
    ): List<PayrollTaxOpeningRow>

    fun list(
        company: UUID,
        year: Int,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<PayrollTaxOpeningRow>

    fun authors(company: UUID, id: UUID): List<UUID>

    fun insert(row: PayrollTaxOpeningsRecord)

    fun advance(company: UUID, id: UUID, version: Long): Long?

    fun append(row: PayrollTaxOpeningRevisionsRecord)
}
