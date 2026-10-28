package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.usecases.GetExpenseReport
import dev.fajar.hris.reporting.domain.usecases.ListExpenseReportClaims
import java.time.LocalDate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/expenses")
class ExpenseReportController(
    private val report: GetExpenseReport,
    private val claims: ListExpenseReportClaims,
) {
    @GetMapping
    fun get(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
    ): ExpenseReportResponse {
        val value = report.execute(actor, from, until).response()
        return ExpenseReportResponse(
            value.companyId,
            value.from,
            value.until,
            value.evaluatedAt,
            value.definitionVersion,
            value.aggregate.totals.toResponse(),
            value.aggregate.statuses.map {
                ExpenseReportStatusResponse(it.status.name, it.counts.toResponse())
            },
        )
    }

    @GetMapping("/claims")
    fun claims(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
        @RequestParam(required = false) status: ExpenseClaimStatus?,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): ExpenseReportClaimPageResponse {
        val value = claims.execute(actor, from, until, status, after, limit).response()
        return ExpenseReportClaimPageResponse(
            value.companyId,
            value.from,
            value.until,
            value.status?.name,
            value.evaluatedAt,
            value.definitionVersion,
            value.page.items.map { it.toResponse() },
            value.page.nextCursor,
        )
    }
}
