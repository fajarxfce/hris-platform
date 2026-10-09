package dev.fajar.hris.communications.delivery.controllers

import dev.fajar.hris.communications.delivery.mappers.toResponse
import dev.fajar.hris.communications.delivery.requests.InboxActionRequest
import dev.fajar.hris.communications.delivery.responses.*
import dev.fajar.hris.communications.domain.entities.InboxAction
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/inbox")
class InboxController(
    private val get: GetInboxItem,
    private val list: ListInbox,
    private val update: UpdateInboxItem,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<InboxSummaryResponse> =
        list.execute(actor, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): InboxItemResponse =
        get.execute(actor, id).response().toResponse()

    @PostMapping("/{id}/read")
    fun read(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: InboxActionRequest,
    ): MutationResponse =
        update
            .execute(actor, operationId, id, body.expectedVersion, InboxAction.READ)
            .response()
            .toResponse()

    @PostMapping("/{id}/acknowledge")
    fun acknowledge(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: InboxActionRequest,
    ): MutationResponse =
        update
            .execute(actor, operationId, id, body.expectedVersion, InboxAction.ACKNOWLEDGE)
            .response()
            .toResponse()
}
