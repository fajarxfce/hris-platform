package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.reporting.delivery.responses.HeadcountReportResponse
import dev.fajar.hris.reporting.domain.usecases.GetHeadcountReport
import java.time.LocalDate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/headcount")
class HeadcountReportController(private val report: GetHeadcountReport) {
    @GetMapping
    fun get(actor: Actor, @RequestParam asOf: LocalDate): HeadcountReportResponse {
        val value = report.execute(actor, asOf).response()
        return HeadcountReportResponse(
            value.companyId,
            value.asOf,
            value.evaluatedAt,
            value.definitionVersion,
            value.counts.employments,
            value.counts.persons,
            value.counts.active,
            value.counts.probation,
            value.counts.suspended,
            value.counts.permanent,
            value.counts.fixedTerm,
        )
    }
}
