package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.requests.RoleTemplateRequest
import dev.fajar.hris.identity.delivery.responses.RoleTemplateResponse
import dev.fajar.hris.identity.delivery.responses.RoleTemplateReviewResponse
import dev.fajar.hris.identity.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/role-templates")
class RoleTemplateController(
    private val list: ListRoleTemplates,
    private val get: GetRoleTemplate,
    private val save: SaveRoleTemplate,
    private val permissions: ListAssignablePermissions,
) {
    @GetMapping("/permissions")
    fun permissions(actor: Actor): List<String> = permissions.execute(actor).response()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<RoleTemplateResponse> =
        list.execute(actor, after, limit).response().let { page ->
            Page(page.items.map { it.toResponse() }, page.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) historyAfter: Long?,
        @RequestParam(defaultValue = "25") historyLimit: Int,
    ): RoleTemplateReviewResponse =
        get.execute(actor, id, historyAfter, historyLimit).response().toResponse()

    @PutMapping("/{id}")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: RoleTemplateRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                key,
                id,
                body.expectedVersion,
                body.code,
                body.name,
                body.permissions,
                body.active,
                body.reason,
            )
            .response()
            .toResponse()
}
