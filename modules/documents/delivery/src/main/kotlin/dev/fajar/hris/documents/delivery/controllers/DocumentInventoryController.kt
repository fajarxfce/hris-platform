package dev.fajar.hris.documents.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.documents.delivery.requests.*
import dev.fajar.hris.documents.delivery.responses.*
import dev.fajar.hris.documents.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/documents/inventory")
class DocumentInventoryController(
    private val start: StartDocumentInventory,
    private val get: GetDocumentInventory,
    private val list: ListDocumentInventories,
    private val attempts: GetDocumentInventoryAttempts,
    private val pages: GetDocumentInventoryPages,
) {
    @PostMapping
    fun start(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: StartDocumentInventoryRequest,
    ) = start.execute(actor, operationId, input.runId, null, input.reason).response().toResponse()

    @PostMapping("/{runId}/resume")
    fun resume(
        actor: Actor,
        @PathVariable runId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: ResumeDocumentInventoryRequest,
    ) =
        start
            .execute(actor, operationId, runId, input.expectedVersion, input.reason)
            .response()
            .toResponse()

    @GetMapping("/{runId}")
    fun get(actor: Actor, @PathVariable runId: UUID) =
        get.execute(actor, runId).response().toResponse(actor)

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<DocumentInventoryResponse> {
        val page = list.execute(actor, after, limit).response()
        return Page(page.items.map { it.toResponse(actor) }, page.nextCursor)
    }

    @GetMapping("/{runId}/attempts")
    fun attempts(actor: Actor, @PathVariable runId: UUID) =
        attempts.execute(actor, runId).response().map { it.toResponse() }

    @GetMapping("/{runId}/pages")
    fun pages(
        actor: Actor,
        @PathVariable runId: UUID,
        @RequestParam(defaultValue = "0") after: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = pages.execute(actor, runId, after, limit).response().map { it.toResponse() }
}
