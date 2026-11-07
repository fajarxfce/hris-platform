package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.requests.VerifyCollaborationLegacyPublicationRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyPublication
import dev.fajar.hris.collaboration.domain.usecases.VerifyNativeCollaborationLegacyPublication
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/collaboration/spaces/{spaceId}/native-device/legacy-runtime")
class NativeCollaborationLegacyPublicationController(
    private val verify: VerifyNativeCollaborationLegacyPublication
) {
    @PostMapping("/publication", consumes = ["application/json"])
    fun verifyPublication(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestBody input: VerifyCollaborationLegacyPublicationRequest,
    ): ResponseEntity<CollaborationLegacyPublication> =
        ResponseEntity.ok()
            .header("Cache-Control", "private, no-store")
            .body(
                verify
                    .execute(
                        actor,
                        identity.sessionId,
                        identity.sessionVersion,
                        spaceId,
                        client.version,
                        input.deviceId,
                        input.expectedDeviceVersion,
                        input.fingerprint,
                    )
                    .response()
            )
}
