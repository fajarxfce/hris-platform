package dev.fajar.hris.organization.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.organization.delivery.mappers.toResponse
import dev.fajar.hris.organization.delivery.requests.UnitRequest
import dev.fajar.hris.organization.delivery.responses.UnitResponse
import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.organization.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/organization-units")
class OrganizationController(
    private val save: SaveOrganizationUnit,
    private val list: ListOrganizationUnits,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) kind: UnitKind?,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<UnitResponse> =
        list.execute(actor, kind, after, limit).response().let {
            Page(it.items.map { unit -> unit.toResponse() }, it.nextCursor)
        }

    @PutMapping("/{id}")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: UnitRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                UnitChange(
                    id,
                    body.code,
                    body.name,
                    body.kind,
                    body.parentId,
                    body.timezone,
                    body.active,
                    body.expectedVersion,
                ),
            )
            .response()
            .toResponse()
}
