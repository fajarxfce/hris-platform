package dev.fajar.hris.reporting.data.dto

import java.math.BigDecimal

data class ExpenseReportAggregateRow(
    val status: String?,
    val claims: Long,
    val employments: Long,
    val lines: Long,
    val amount: BigDecimal,
)
