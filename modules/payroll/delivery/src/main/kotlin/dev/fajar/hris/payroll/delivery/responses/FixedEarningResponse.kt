package dev.fajar.hris.payroll.delivery.responses

data class FixedEarningResponse(
    val code: String,
    val name: String,
    val amount: String,
    val taxable: Boolean,
)
