package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.usecases.GetReportCatalogue
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/catalogue")
class ReportCatalogueController(private val catalogue: GetReportCatalogue) {
    @GetMapping
    fun get(actor: Actor): ReportCatalogueResponse {
        val value = catalogue.execute(actor).response()
        return ReportCatalogueResponse(
            value.companyId,
            value.evaluatedAt,
            value.reports.map { ReportCatalogueEntryResponse(it, it.definitionVersion) },
        )
    }
}
