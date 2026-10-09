package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.PayrollPaymentBatchesRecord

data class PayrollPaymentSummaryRow(
    val batch: PayrollPaymentBatchesRecord,
    val pending: Int,
    val succeeded: Int,
    val failed: Int,
)
