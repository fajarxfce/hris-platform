package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.delivery.responses.CompanyHeadcountResponse
import dev.fajar.hris.reporting.delivery.responses.GroupHeadcountReportResponse
import dev.fajar.hris.reporting.domain.usecases.GetGroupHeadcountReport
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/reports/headcount")
class GroupHeadcountReportController(private val report: GetGroupHeadcountReport) {
    @GetMapping
    fun get(
        actor: Actor,
        client: ClientRequest,
        @RequestParam companies: List<UUID>,
        @RequestParam asOf: LocalDate,
    ): GroupHeadcountReportResponse {
        val value = report.execute(actor, companies, asOf, client).response()
        return GroupHeadcountReportResponse(
            value.asOf,
            value.evaluatedAt,
            value.definitionVersion,
            value.counts.totals.toResponse(),
            value.counts.companies.map {
                CompanyHeadcountResponse(it.companyId, it.counts.toResponse())
            },
        )
    }
}
