package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceDirectTransport
import dev.fajar.hris.collaboration.domain.usecases.ReadNativeCollaborationSpaceDirectTransport
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping(
    "/api/v1/collaboration/spaces/{spaceId}/direct-conversations/{conversationId}/transport"
)
class NativeCollaborationSpaceDirectTransportController(
    private val read: ReadNativeCollaborationSpaceDirectTransport
) {
    @GetMapping
    fun get(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @PathVariable conversationId: UUID,
    ): CollaborationSpaceDirectTransport =
        read
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                conversationId,
                client.version,
            )
            .response()
}
