package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.usecases.GetAttendanceExceptionReport
import java.time.LocalDate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/attendance-exceptions")
class AttendanceExceptionReportController(private val report: GetAttendanceExceptionReport) {
    @GetMapping
    fun get(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
    ): AttendanceExceptionReportResponse {
        val value = report.execute(actor, from, until).response()
        return AttendanceExceptionReportResponse(
            value.companyId,
            value.from,
            value.until,
            value.evaluatedAt,
            value.definitionVersion,
            value.aggregate.totals.toResponse(),
            value.aggregate.days.map {
                AttendanceExceptionDayResponse(it.date, it.counts.toResponse())
            },
        )
    }
}
