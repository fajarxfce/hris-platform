package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.requests.AccountAccessRequest
import dev.fajar.hris.identity.delivery.responses.ManagedAccountResponse
import dev.fajar.hris.identity.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/identity/accounts")
class AccountAdministrationController(
    private val list: ListManagedAccounts,
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
            Page(
                page.items.map {
                    ManagedAccountResponse(
                        it.account.id,
                        it.account.email,
                        it.account.displayName,
                        it.account.active,
                        it.invitationPending,
                        it.account.mfaConfigured,
                        it.platformPermissions,
                        it.account.version,
                    )
                },
                page.nextCursor,
            )
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
