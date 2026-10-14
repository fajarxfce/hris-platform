package dev.fajar.hris.payroll.domain.entities

data class PayrollTaxOpeningEmployee(
    val employee: PayrollEmployee,
    val openingVersion: Long?,
    val openingStatus: PayrollTaxOpeningStatus?,
)
