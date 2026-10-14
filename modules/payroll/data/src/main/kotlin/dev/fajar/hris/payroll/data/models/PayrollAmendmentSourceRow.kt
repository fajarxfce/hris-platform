package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.PayrollRunTargetsRecord
import java.time.LocalDate
import java.util.UUID

data class PayrollAmendmentSourceRow(
    val companyId: UUID,
    val assessmentId: UUID,
    val personId: UUID,
    val taxMonth: LocalDate,
    val target: PayrollRunTargetsRecord,
    val opening: PayrollTaxOpeningRow,
    val facts: String,
    val calculation: String,
)
