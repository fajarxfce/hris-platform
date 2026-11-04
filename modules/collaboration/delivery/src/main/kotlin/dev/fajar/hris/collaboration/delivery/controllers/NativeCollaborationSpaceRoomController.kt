package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.requests.RequestCollaborationSpaceRoomRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomView
import dev.fajar.hris.collaboration.domain.usecases.ReadNativeCollaborationSpaceRoom
import dev.fajar.hris.collaboration.domain.usecases.RequestNativeCollaborationSpaceRoom
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/collaboration/spaces/{spaceId}/direct-conversations/{conversationId}/room")
class NativeCollaborationSpaceRoomController(
    private val request: RequestNativeCollaborationSpaceRoom,
    private val status: ReadNativeCollaborationSpaceRoom,
) {
    @PostMapping(consumes = ["application/json"])
    fun request(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @PathVariable conversationId: UUID,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: RequestCollaborationSpaceRoomRequest,
    ): MutationResponse =
        request
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                operation,
                conversationId,
                input.deviceId,
                input.expectedDeviceVersion,
                input.expectedRequestRevision,
                client.version,
                input.expectedRequestAccountId,
            )
            .response()
            .toResponse()

    @GetMapping
    fun get(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @PathVariable conversationId: UUID,
    ): CollaborationSpaceRoomView =
        status
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
