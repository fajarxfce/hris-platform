package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.requests.ReadCollaborationSpaceLegacyRoomRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceLegacyConversation
import dev.fajar.hris.collaboration.domain.usecases.ReadNativeCollaborationSpaceDirect
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/collaboration/spaces/{spaceId}/legacy-rooms")
class NativeCollaborationSpaceLegacyRoomController(
    private val read: ReadNativeCollaborationSpaceDirect
) {
    @PostMapping("/read", consumes = ["application/json"])
    fun read(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestBody input: ReadCollaborationSpaceLegacyRoomRequest,
    ): CollaborationSpaceLegacyConversation =
        read
            .legacyRoom(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                input.companyId,
                input.matrixRoomId,
            )
            .response()
}
