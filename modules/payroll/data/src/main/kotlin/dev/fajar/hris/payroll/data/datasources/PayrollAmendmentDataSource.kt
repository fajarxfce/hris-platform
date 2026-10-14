package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface PayrollAmendmentDataSource {
    fun summary(company: UUID, id: UUID): PayrollAmendmentSummaryRow?

    fun find(company: UUID, id: UUID): PayrollAmendmentRow?

    fun revision(company: UUID, id: UUID, version: Long): PayrollAmendmentRow?

    fun history(company: UUID, id: UUID, after: Long?, limit: Int): List<PayrollAmendmentSummaryRow>

    fun list(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        employeeId: UUID?,
        status: String?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<PayrollAmendmentSummaryRow>

    fun insert(row: PayrollAmendmentsRecord)

    fun advance(company: UUID, id: UUID, expectedVersion: Long): Long?

    fun append(row: PayrollAmendmentRevisionsRecord)

    fun calculationSummary(
        company: UUID,
        amendmentId: UUID,
        calculationId: UUID,
    ): PayrollAmendmentCalculationsRecord?

    fun calculation(
        company: UUID,
        amendmentId: UUID,
        calculationId: UUID,
    ): PayrollAmendmentCalculationRow?

    fun results(company: UUID, calculationId: UUID): List<PayrollAmendmentResultRow>

    fun insertCalculation(row: PayrollAmendmentCalculationsRecord)

    fun insertResults(rows: List<PayrollAmendmentResultsRecord>)
}
