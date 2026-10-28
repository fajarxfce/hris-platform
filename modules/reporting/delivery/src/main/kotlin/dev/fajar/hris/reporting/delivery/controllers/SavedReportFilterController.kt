package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.delivery.requests.*
import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/saved-filters")
class SavedReportFilterController(
    private val list: ListSavedReportFilters,
    private val get: GetSavedReportFilter,
    private val save: SaveReportFilter,
    private val delete: DeleteReportFilter,
) {
    @GetMapping
    fun list(actor: Actor): SavedReportFiltersResponse =
        SavedReportFiltersResponse(
            requireNotNull(actor.companyId),
            list.execute(actor).response().map { it.toResponse() },
        )

    @GetMapping("/{filterId}")
    fun get(actor: Actor, @PathVariable filterId: UUID): SavedReportFilterResponse =
        get.execute(actor, filterId).response().toResponse()

    @PutMapping("/{filterId}", consumes = ["application/json"])
    fun save(
        actor: Actor,
        @PathVariable filterId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: SaveReportFilterRequest,
    ): MutationResponse {
        val p = input.parameters
        return save
            .execute(
                actor,
                filterId,
                operationId,
                SavedReportFilterDefinition(
                    input.name,
                    input.kind,
                    input.definitionVersion,
                    ReportFilterParameters(p.asOf, p.from, p.until, p.status, p.companies),
                ),
                input.expectedVersion,
            )
            .response()
            .toResponse()
    }

    @PostMapping("/{filterId}/delete", consumes = ["application/json"])
    fun delete(
        actor: Actor,
        @PathVariable filterId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: DeleteReportFilterRequest,
    ): MutationResponse =
        delete.execute(actor, filterId, operationId, input.expectedVersion).response().toResponse()
}
