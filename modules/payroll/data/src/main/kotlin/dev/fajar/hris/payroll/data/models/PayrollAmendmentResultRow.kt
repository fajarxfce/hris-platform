package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.PayrollAmendmentResultsRecord
import dev.fajar.hris.schema.tables.records.PayrollRunTargetsRecord

data class PayrollAmendmentResultRow(
    val result: PayrollAmendmentResultsRecord,
    val target: PayrollRunTargetsRecord,
)
