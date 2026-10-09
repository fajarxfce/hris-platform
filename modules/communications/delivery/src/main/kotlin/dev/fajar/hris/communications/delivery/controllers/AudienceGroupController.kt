package dev.fajar.hris.communications.delivery.controllers

import dev.fajar.hris.communications.delivery.mappers.toResponse
import dev.fajar.hris.communications.delivery.requests.SaveAudienceGroupRequest
import dev.fajar.hris.communications.delivery.responses.*
import dev.fajar.hris.communications.domain.entities.SaveAudienceGroupCommand
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/communications/audience-groups")
class AudienceGroupController(
    private val save: SaveAudienceGroup,
    private val get: GetAudienceGroup,
    private val list: ListAudienceGroups,
) {
    @PutMapping("/{id}")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: SaveAudienceGroupRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                SaveAudienceGroupCommand(
                    id,
                    body.expectedVersion,
                    body.name,
                    body.active,
                    body.employmentIds,
                    body.reason,
                ),
            )
            .response()
            .toResponse()

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): AudienceGroupResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping("/{id}/revisions/{version}")
    fun revision(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable version: Long,
    ): AudienceGroupResponse = get.execute(actor, id, version).response().toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<AudienceGroupSummaryResponse> =
        list.execute(actor, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }
}
