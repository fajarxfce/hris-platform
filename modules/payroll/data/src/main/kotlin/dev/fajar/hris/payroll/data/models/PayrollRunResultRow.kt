package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.*
import java.time.*

data class PayrollRunResultRow(
    val target: PayrollRunTargetsRecord,
    val result: PayrollRunResultsRecord,
)
