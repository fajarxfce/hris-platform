package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollInputSummaryRow
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface PayrollInputDataSource {
    fun find(company: UUID, employee: UUID, month: LocalDate): PayrollInputRevisionsRecord?

    fun revision(
        company: UUID,
        employee: UUID,
        month: LocalDate,
        revision: Long,
    ): PayrollInputRevisionsRecord?

    fun list(
        company: UUID,
        month: LocalDate,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<PayrollInputSummaryRow>

    fun history(
        company: UUID,
        employee: UUID,
        month: LocalDate,
        after: Long?,
        limit: Int,
    ): List<PayrollInputSummaryRow>

    fun authors(company: UUID, id: UUID): List<UUID>

    fun insert(row: PayrollInputsRecord)

    fun advance(company: UUID, id: UUID, version: Long): Long?

    fun append(row: PayrollInputRevisionsRecord)
}
