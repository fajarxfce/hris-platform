package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.usecases.GetAttendanceClosingReport
import dev.fajar.hris.reporting.domain.usecases.ListAttendanceClosingEmployees
import java.time.YearMonth
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/attendance-closings")
class AttendanceClosingReportController(
    private val report: GetAttendanceClosingReport,
    private val employees: ListAttendanceClosingEmployees,
) {
    @GetMapping("/employees")
    fun employees(
        actor: Actor,
        @RequestParam month: YearMonth,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): AttendanceClosingEmployeePageResponse {
        val value = employees.execute(actor, month, after, limit).response()
        return AttendanceClosingEmployeePageResponse(
            value.companyId,
            value.month,
            value.evaluatedAt,
            value.definitionVersion,
            value.page.items.map { row ->
                AttendanceClosingEmployeeResponse(
                    row.employeeId,
                    row.employeeNumber,
                    row.employeeName,
                    row.closing.toResponse(),
                    row.adjustment?.let {
                        AttendanceClosingAdjustmentReferenceResponse(
                            it.id,
                            it.revision,
                            it.recordedAt,
                        )
                    },
                    row.counts.toResponse(),
                )
            },
            value.page.nextCursor,
        )
    }

    @GetMapping
    fun get(
        actor: Actor,
        @RequestParam from: YearMonth,
        @RequestParam until: YearMonth,
    ): AttendanceClosingReportResponse {
        val value = report.execute(actor, from, until).response()
        return AttendanceClosingReportResponse(
            value.companyId,
            value.from,
            value.until,
            value.evaluatedAt,
            value.definitionVersion,
            value.aggregate.totals.toResponse(),
            value.aggregate.months.map {
                AttendanceClosingReportMonthResponse(
                    it.month,
                    it.closing?.toResponse(),
                    it.counts.toResponse(),
                )
            },
        )
    }
}
