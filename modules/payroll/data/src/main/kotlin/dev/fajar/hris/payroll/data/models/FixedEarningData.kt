package dev.fajar.hris.payroll.data.models

data class FixedEarningData(
    val code: String,
    val name: String,
    val amount: String,
    val taxable: Boolean,
)
