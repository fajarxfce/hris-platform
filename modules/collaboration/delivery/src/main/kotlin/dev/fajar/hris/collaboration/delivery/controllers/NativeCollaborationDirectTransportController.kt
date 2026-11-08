package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationDirectTransport
import dev.fajar.hris.collaboration.domain.usecases.ReadNativeCollaborationDirectTransport
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping(
    "/api/v1/companies/{companyId}/collaboration/direct-conversations/{conversationId}/transport"
)
class NativeCollaborationDirectTransportController(
    private val read: ReadNativeCollaborationDirectTransport
) {
    @GetMapping
    fun get(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable conversationId: UUID,
    ): CollaborationDirectTransport =
        read
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                client.version,
                conversationId,
            )
            .response()
}
