package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.usecases.GetOvertimeReport
import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import java.time.LocalDate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/overtime")
class OvertimeReportController(private val report: GetOvertimeReport) {
    @GetMapping
    fun get(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
        @RequestParam(required = false) status: OvertimeStatus?,
    ): OvertimeReportResponse {
        val value = report.execute(actor, from, until, status).response()
        return OvertimeReportResponse(
            value.companyId,
            value.from,
            value.until,
            value.status?.name,
            value.evaluatedAt,
            value.definitionVersion,
            value.aggregate.totals.toResponse(),
            value.aggregate.months.map {
                OvertimeReportMonthResponse(it.month, it.from, it.until, it.counts.toResponse())
            },
        )
    }
}
