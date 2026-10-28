package dev.fajar.hris.reporting.delivery.responses

import java.time.YearMonth

data class PayrollCostReportMonthResponse(
    val month: YearMonth,
    val counts: PayrollCostReportCountsResponse,
)
