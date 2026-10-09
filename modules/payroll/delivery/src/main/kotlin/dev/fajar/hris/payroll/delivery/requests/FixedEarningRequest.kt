package dev.fajar.hris.payroll.delivery.requests

data class FixedEarningRequest(
    val code: String,
    val name: String,
    val amount: String,
    val taxable: Boolean = true,
)
