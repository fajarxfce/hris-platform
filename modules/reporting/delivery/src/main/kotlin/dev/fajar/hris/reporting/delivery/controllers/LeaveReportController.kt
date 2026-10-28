package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.leave.domain.entities.LeaveStatus
import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.usecases.GetLeaveReport
import dev.fajar.hris.reporting.domain.usecases.ListLeaveReportRequests
import java.time.LocalDate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/leave")
class LeaveReportController(
    private val report: GetLeaveReport,
    private val requests: ListLeaveReportRequests,
) {
    @GetMapping
    fun get(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
    ): LeaveReportResponse {
        val value = report.execute(actor, from, until).response()
        return LeaveReportResponse(
            value.companyId,
            value.from,
            value.until,
            value.evaluatedAt,
            value.definitionVersion,
            value.aggregate.totals.toResponse(),
            value.aggregate.statuses.map {
                LeaveReportStatusResponse(it.status.name, it.counts.toResponse())
            },
        )
    }

    @GetMapping("/requests")
    fun requests(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
        @RequestParam(required = false) status: LeaveStatus?,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): LeaveReportRequestPageResponse {
        val value = requests.execute(actor, from, until, status, after, limit).response()
        return LeaveReportRequestPageResponse(
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
