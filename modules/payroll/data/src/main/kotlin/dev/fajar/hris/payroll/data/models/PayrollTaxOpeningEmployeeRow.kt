package dev.fajar.hris.payroll.data.models

data class PayrollTaxOpeningEmployeeRow(
    val employee: PayrollEmployeeRow,
    val openingVersion: Long?,
    val openingStatus: String?,
)
