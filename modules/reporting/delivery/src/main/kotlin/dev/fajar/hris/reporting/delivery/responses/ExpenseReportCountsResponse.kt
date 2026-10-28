package dev.fajar.hris.reporting.delivery.responses

data class ExpenseReportCountsResponse(
    val claims: Long,
    val employments: Long,
    val lines: Long,
    val amount: String,
)
