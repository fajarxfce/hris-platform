package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.requests.ReadCollaborationSpaceProviderRoomRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomQuery
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomView
import dev.fajar.hris.collaboration.domain.usecases.ReadNativeCollaborationSpaceRoom
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/collaboration/spaces/{spaceId}/rooms")
class NativeCollaborationSpaceProviderRoomController(
    private val read: ReadNativeCollaborationSpaceRoom
) {
    @PostMapping("/read", consumes = ["application/json"])
    fun read(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestBody input: ReadCollaborationSpaceProviderRoomRequest,
    ): CollaborationSpaceRoomView =
        read
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                CollaborationSpaceRoomQuery.ProviderRoom(input.matrixRoomId),
                client.version,
            )
            .response()
}
