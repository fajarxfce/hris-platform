package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.requests.AccountAccessRequest
import dev.fajar.hris.identity.delivery.responses.ManagedAccountResponse
import dev.fajar.hris.identity.delivery.responses.ManagedAccountReviewResponse
import dev.fajar.hris.identity.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/identity/accounts")
class AccountAdministrationController(
    private val list: ListManagedAccounts,
    private val get: GetManagedAccount,
    private val save: SaveAccountAccess,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ManagedAccountResponse> =
        list.execute(actor, query, after, limit).response().let { page ->
            Page(page.items.map { it.toResponse() }, page.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): ManagedAccountReviewResponse =
        get.execute(actor, id).response().let {
            ManagedAccountReviewResponse(it.account.toResponse(), it.assignablePermissions)
        }

    @PutMapping("/{id}/access")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: AccountAccessRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                key,
                id,
                body.expectedVersion,
                body.active,
                body.platformPermissions,
                body.reason,
            )
            .response()
            .toResponse()
}
