package dev.fajar.hris.reporting.domain.entities

import java.time.YearMonth

data class PayrollCostReportMonth(val month: YearMonth, val counts: PayrollCostReportCounts)
