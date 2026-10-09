package dev.fajar.hris.payroll.delivery.responses

data class PayrollVariableEarningResponse(
    val code: String,
    val name: String,
    val amount: String,
    val taxable: Boolean,
)
