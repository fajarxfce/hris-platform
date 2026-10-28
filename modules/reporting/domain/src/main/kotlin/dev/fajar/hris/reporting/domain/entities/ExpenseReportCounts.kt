package dev.fajar.hris.reporting.domain.entities

import java.math.BigDecimal

data class ExpenseReportCounts(
    val claims: Long = 0,
    val employments: Long = 0,
    val lines: Long = 0,
    val amount: BigDecimal = BigDecimal.ZERO.setScale(2),
)
