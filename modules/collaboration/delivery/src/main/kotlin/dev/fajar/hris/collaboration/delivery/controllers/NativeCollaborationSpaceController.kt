package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.responses.NativeCollaborationSpaceAdmissionResponse
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceContact
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
@RequestMapping("/api/v1/collaboration/spaces")
class NativeCollaborationSpaceController(
    private val admission: AcquireNativeCollaborationSpaceAdmission,
    private val contacts: ListNativeCollaborationSpaceContacts,
    private val select: SelectNativeCollaborationSpaceIdentity,
) {
    @GetMapping("/current")
    fun currentSpace(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
    ) =
        NativeCollaborationSpaceAdmissionResponse(
            admission
                .execute(actor, identity.sessionId, identity.sessionVersion, client.version)
                .response()
        )

    @GetMapping("/{spaceId}/contacts")
    fun spaceContacts(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "30") limit: Int,
    ): Page<CollaborationSpaceContact> =
        contacts
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                query,
                after,
                limit,
            )
            .response()

    @PostMapping("/{spaceId}/identity")
    fun selectIdentity(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestHeader("Idempotency-Key") operation: UUID,
    ): MutationResponse =
        select
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                operation,
            )
            .response()
            .toResponse()
}
