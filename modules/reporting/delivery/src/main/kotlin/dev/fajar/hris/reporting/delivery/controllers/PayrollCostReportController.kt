package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.usecases.GetPayrollCostReport
import java.time.YearMonth
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/payroll-costs")
class PayrollCostReportController(private val report: GetPayrollCostReport) {
    @GetMapping
    fun get(
        actor: Actor,
        @RequestParam from: YearMonth,
        @RequestParam until: YearMonth,
    ): PayrollCostReportResponse {
        val value = report.execute(actor, from, until).response()
        return PayrollCostReportResponse(
            value.companyId,
            value.from,
            value.until,
            value.evaluatedAt,
            value.definitionVersion,
            value.currency,
            value.aggregate.totals.toResponse(),
            value.aggregate.months.map {
                PayrollCostReportMonthResponse(it.month, it.counts.toResponse())
            },
        )
    }
}
