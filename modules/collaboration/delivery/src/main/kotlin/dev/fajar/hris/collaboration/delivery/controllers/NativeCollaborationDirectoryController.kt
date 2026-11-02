package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.collaboration.delivery.responses.NativeCollaborationDirectoryResponse
import dev.fajar.hris.collaboration.domain.usecases.ListNativeCollaborationDevices
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/collaboration/native-devices")
class NativeCollaborationDirectoryController(private val list: ListNativeCollaborationDevices) {
    @GetMapping
    @PendingMfaAllowed
    fun list(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        @RequestParam(required = false) afterCompanyId: UUID?,
        @RequestParam(defaultValue = "8") limit: Int,
    ): NativeCollaborationDirectoryResponse =
        NativeCollaborationDirectoryResponse(
            list
                .execute(actor, identity.sessionId, identity.sessionVersion, afterCompanyId, limit)
                .response()
        )
}
