package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.requests.RequestCollaborationDirectRoomRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationDirectRoomStatus
import dev.fajar.hris.collaboration.domain.usecases.GetNativeCollaborationDirectRoomStatus
import dev.fajar.hris.collaboration.domain.usecases.RequestNativeCollaborationDirectRoom
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping(
    "/api/v1/companies/{companyId}/collaboration/direct-conversations/{conversationId}/room"
)
class NativeCollaborationDirectRoomController(
    private val request: RequestNativeCollaborationDirectRoom,
    private val status: GetNativeCollaborationDirectRoomStatus,
) {
    @PostMapping(consumes = ["application/json"])
    fun request(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable conversationId: UUID,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: RequestCollaborationDirectRoomRequest,
    ): MutationResponse =
        request
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                client.version,
                operation,
                conversationId,
                input.deviceId,
                input.expectedDeviceVersion,
                input.expectedProvisioningVersion,
            )
            .response()
            .toResponse()

    @GetMapping
    fun get(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable conversationId: UUID,
    ): CollaborationDirectRoomStatus =
        status
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                client.version,
                conversationId,
            )
            .response()
}
