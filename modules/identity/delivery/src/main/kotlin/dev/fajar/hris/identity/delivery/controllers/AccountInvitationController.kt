package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.requests.AccountInvitationRequest
import dev.fajar.hris.identity.domain.usecases.InviteAccount
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
class AccountInvitationController(private val invite: InviteAccount) {
    @PostMapping("/api/v1/identity/invitations")
    fun invite(
        actor: Actor,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: AccountInvitationRequest,
    ): MutationResponse =
        invite
            .execute(
                actor,
                key,
                body.id,
                body.email,
                body.displayName,
                body.reason,
                body.expectedVersion,
            )
            .response()
            .toResponse()
}
