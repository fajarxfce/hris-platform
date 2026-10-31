package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.requests.OpenCollaborationDirectRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationDirectConversation
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/companies/{companyId}/collaboration/direct-conversations")
class NativeCollaborationDirectController(
    private val open: OpenNativeCollaborationDirect,
    private val read: ReadNativeCollaborationDirect,
) {
    @PostMapping(consumes = ["application/json"])
    fun open(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: OpenCollaborationDirectRequest,
    ): MutationResponse =
        open
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                client.version,
                operation,
                input.peerAccountId,
            )
            .response()
            .toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "30") limit: Int,
    ): Page<CollaborationDirectConversation> =
        read
            .list(actor, identity.sessionId, identity.sessionVersion, client.version, after, limit)
            .response()

    @GetMapping("/{conversationId}")
    fun get(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable conversationId: UUID,
    ): CollaborationDirectConversation =
        read
            .get(actor, identity.sessionId, identity.sessionVersion, client.version, conversationId)
            .response()
}
