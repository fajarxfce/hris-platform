package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.PayrollTaxOpeningRevisionsRecord
import java.util.UUID

data class PayrollTaxOpeningRow(
    val employeeId: UUID,
    val year: Int,
    val revision: PayrollTaxOpeningRevisionsRecord,
)
