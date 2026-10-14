package dev.fajar.hris.payroll.data.models

data class PayrollInputEmployeeRow(
    val employee: PayrollEmployeeRow,
    val inputVersion: Long?,
    val inputStatus: String?,
)
