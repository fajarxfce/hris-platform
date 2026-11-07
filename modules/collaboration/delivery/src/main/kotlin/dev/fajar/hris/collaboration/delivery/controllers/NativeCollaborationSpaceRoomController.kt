package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.delivery.requests.ReadCollaborationSpaceRoomIntentReceiptRequest
import dev.fajar.hris.collaboration.delivery.requests.ReadCollaborationSpaceRoomReceiptRequest
import dev.fajar.hris.collaboration.delivery.requests.RequestCollaborationSpaceRoomRequest
import dev.fajar.hris.collaboration.delivery.responses.NativeCollaborationSpaceRoomIntentReceiptResponse
import dev.fajar.hris.collaboration.delivery.responses.NativeCollaborationSpaceRoomReceiptResponse
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomObservedAttempt
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomOperation
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomRequestFormat
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomRequestIntent
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomView
import dev.fajar.hris.collaboration.domain.usecases.ReadNativeCollaborationSpaceRoom
import dev.fajar.hris.collaboration.domain.usecases.ReadNativeCollaborationSpaceRoomReceipt
import dev.fajar.hris.collaboration.domain.usecases.RequestNativeCollaborationSpaceRoom
import dev.fajar.hris.core.domain.*
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
    private val readReceipt: ReadNativeCollaborationSpaceRoomReceipt,
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

    @PostMapping("/requests", consumes = ["application/json"])
    fun requestIntent(
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
                format = CollaborationSpaceRoomRequestFormat.STABLE_INTENT,
            )
            .response()
            .toResponse()

    @PostMapping("/requests/receipts/read", consumes = ["application/json"])
    fun readIntentReceipt(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @PathVariable conversationId: UUID,
        @RequestBody input: ReadCollaborationSpaceRoomIntentReceiptRequest,
    ): NativeCollaborationSpaceRoomIntentReceiptResponse {
        if ((input.expectedRequestRevision == null) != (input.expectedRequestAccountId == null))
            return Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_collaboration_room_operation")
                )
                .response()
        val original =
            CollaborationSpaceRoomRequestIntent(
                input.operationId,
                spaceId,
                conversationId,
                input.deviceId,
                input.expectedDeviceVersion,
                input.expectedRequestRevision?.let {
                    CollaborationSpaceRoomObservedAttempt(
                        requireNotNull(input.expectedRequestAccountId),
                        it,
                    )
                },
            )
        val observed =
            readReceipt
                .execute(
                    actor,
                    identity.sessionId,
                    identity.sessionVersion,
                    client.version,
                    original,
                )
                .response()
        return NativeCollaborationSpaceRoomIntentReceiptResponse(actor.accountId, observed)
    }

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

    @PostMapping("/receipts/read", consumes = ["application/json"])
    fun readReceipt(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @PathVariable conversationId: UUID,
        @RequestBody input: ReadCollaborationSpaceRoomReceiptRequest,
    ): NativeCollaborationSpaceRoomReceiptResponse {
        if ((input.expectedRequestRevision == null) != (input.expectedRequestAccountId == null))
            return Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_collaboration_room_operation")
                )
                .response()
        val original =
            CollaborationSpaceRoomOperation(
                input.operationId,
                spaceId,
                conversationId,
                input.nativeSessionId,
                input.nativeSessionVersion,
                input.deviceId,
                input.expectedDeviceVersion,
                input.expectedRequestRevision?.let {
                    CollaborationSpaceRoomObservedAttempt(
                        requireNotNull(input.expectedRequestAccountId),
                        it,
                    )
                },
                ClientVersion(input.platform, input.clientBuild),
            )
        val observed =
            readReceipt
                .execute(
                    actor,
                    identity.sessionId,
                    identity.sessionVersion,
                    client.version,
                    original,
                )
                .response()
        return NativeCollaborationSpaceRoomReceiptResponse(actor.accountId, observed)
    }
}
