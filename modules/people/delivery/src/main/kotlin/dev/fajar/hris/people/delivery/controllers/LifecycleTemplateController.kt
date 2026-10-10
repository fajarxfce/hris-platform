package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.mappers.*
import dev.fajar.hris.people.delivery.requests.*
import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/lifecycle")
class LifecycleTemplateController(
    private val save: SaveLifecycleTemplate,
    private val list: ListLifecycleTemplates,
    private val get: GetLifecycleTemplate,
) {
    @PutMapping("/templates/{id}")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: LifecycleTemplateRequest,
    ): MutationResponse =
        save
            .execute(actor, key, body.toTemplate(id), body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/templates/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): LifecycleTemplateResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping("/templates")
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<LifecycleTemplateResponse> =
        list.execute(actor, after, limit).response().let {
            Page(it.items.map { template -> template.toResponse() }, it.nextCursor)
        }
}
