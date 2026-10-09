package dev.fajar.hris.communications.delivery.controllers

import dev.fajar.hris.communications.delivery.mappers.toResponse
import dev.fajar.hris.communications.delivery.requests.SaveAnnouncementRequest
import dev.fajar.hris.communications.delivery.responses.*
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/announcements")
class AnnouncementController(
    private val save: SaveAnnouncement,
    private val get: GetAnnouncement,
    private val list: ListAnnouncements,
    private val history: ListAnnouncementHistory,
) {
    @PutMapping("/{id}")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: SaveAnnouncementRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                SaveAnnouncementCommand(
                    id,
                    body.expectedVersion,
                    body.title,
                    body.body,
                    AnnouncementAudience(body.audience.kind, body.audience.targetIds),
                    body.acknowledgementRequired,
                    body.reason,
                ),
            )
            .response()
            .toResponse()

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): AnnouncementResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping("/{id}/revisions/{version}")
    fun revision(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable version: Long,
    ): AnnouncementResponse = get.execute(actor, id, version).response().toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<AnnouncementSummaryResponse> =
        list.execute(actor, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}/history")
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<AnnouncementSummaryResponse> =
        history.execute(actor, id, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }
}
