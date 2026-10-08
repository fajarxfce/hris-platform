package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.requests.OidcIdentityRequest
import dev.fajar.hris.identity.delivery.responses.OidcIdentityResponse
import dev.fajar.hris.identity.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/identity/accounts/{accountId}/oidc")
class OidcIdentityController(
    private val list: ListOidcIdentities,
    private val save: SaveOidcIdentity,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @PathVariable accountId: UUID,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<OidcIdentityResponse> =
        list.execute(actor, accountId, after, size).response().let { page ->
            Page(
                page.items.map {
                    OidcIdentityResponse(
                        it.id,
                        it.issuer,
                        it.subject,
                        it.active,
                        it.version,
                        it.linkedAt,
                    )
                },
                page.nextCursor,
            )
        }

    @PutMapping("/{id}")
    fun save(
        actor: Actor,
        @PathVariable accountId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: OidcIdentityRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                accountId,
                id,
                body.issuer,
                body.subject,
                body.active,
                body.expectedVersion,
                body.reason,
            )
            .response()
            .toResponse()
}
