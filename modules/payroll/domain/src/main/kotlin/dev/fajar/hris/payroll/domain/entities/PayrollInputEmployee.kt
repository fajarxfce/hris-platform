package dev.fajar.hris.payroll.domain.entities

data class PayrollInputEmployee(
    val employee: PayrollEmployee,
    val inputVersion: Long?,
    val inputStatus: PayrollInputStatus?,
)
