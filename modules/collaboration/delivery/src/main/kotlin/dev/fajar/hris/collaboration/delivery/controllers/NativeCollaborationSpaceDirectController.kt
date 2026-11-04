package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.requests.OpenCollaborationDirectRequest
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/collaboration/spaces/{spaceId}")
class NativeCollaborationSpaceDirectController(
    private val open: OpenNativeCollaborationSpaceDirect,
    private val read: ReadNativeCollaborationSpaceDirect,
) {
    @PostMapping("/direct-conversations", consumes = ["application/json"])
    fun open(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: OpenCollaborationDirectRequest,
    ): MutationResponse =
        open
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                operation,
                input.peerAccountId,
            )
            .response()
            .toResponse()

    @GetMapping("/direct-conversations")
    fun list(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "30") limit: Int,
    ): Page<CollaborationSpaceDirectConversation> =
        read
            .list(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                after,
                limit,
            )
            .response()

    @GetMapping("/direct-conversations/{conversationId}")
    fun get(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @PathVariable conversationId: UUID,
    ): CollaborationSpaceDirectConversation =
        read
            .get(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                conversationId,
            )
            .response()

    @GetMapping("/legacy-conversations")
    fun legacy(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestParam(required = false) afterCompanyId: UUID?,
        @RequestParam(required = false) afterConversationId: UUID?,
        @RequestParam(defaultValue = "30") limit: Int,
    ): CollaborationSpaceLegacyConversationPage {
        if ((afterCompanyId == null) != (afterConversationId == null))
            return Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_collaboration_conversation_query")
                )
                .response()
        val after =
            afterCompanyId?.let {
                CollaborationLegacyConversationPosition(it, requireNotNull(afterConversationId))
            }
        return read
            .legacy(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                after,
                limit,
            )
            .response()
    }

    @GetMapping("/legacy-conversations/{legacyCompanyId}/{conversationId}")
    fun legacyOne(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @PathVariable legacyCompanyId: UUID,
        @PathVariable conversationId: UUID,
    ): CollaborationSpaceLegacyConversation =
        read
            .legacyOne(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                CollaborationLegacyConversationPosition(legacyCompanyId, conversationId),
            )
            .response()
}
