package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationParticipantReference
import dev.fajar.hris.collaboration.domain.usecases.ListNativeCollaborationParticipants
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/companies/{companyId}/collaboration/participants")
class NativeCollaborationParticipantController(
    private val participants: ListNativeCollaborationParticipants
) {
    @GetMapping
    fun list(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "30") limit: Int,
    ): Page<CollaborationParticipantReference> =
        participants
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                client.version,
                query,
                after,
                limit,
            )
            .response()
}
