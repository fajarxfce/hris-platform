package dev.fajar.hris.payroll.data.models

data class PayrollVariableEarningData(
    val code: String,
    val name: String,
    val amount: String,
    val taxable: Boolean,
)
