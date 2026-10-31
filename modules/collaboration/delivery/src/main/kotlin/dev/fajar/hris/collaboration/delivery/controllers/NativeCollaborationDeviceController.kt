package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.requests.*
import dev.fajar.hris.collaboration.delivery.responses.NativeCollaborationDeviceResponse
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/companies/{companyId}/collaboration/native-device")
class NativeCollaborationDeviceController(
    private val register: RegisterNativeCollaborationDevice,
    private val status: GetNativeCollaborationDeviceStatus,
    private val retry: RetryNativeCollaborationProvisioning,
) {
    @GetMapping
    fun status(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
    ): NativeCollaborationDeviceResponse =
        NativeCollaborationDeviceResponse(
            status
                .execute(actor, identity.sessionId, identity.sessionVersion, client.version)
                .response()
        )

    @PostMapping("/retry", consumes = ["application/json"])
    fun retry(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: RetryCollaborationProvisioningRequest,
    ): MutationResponse =
        retry
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                operation,
                input.deviceId,
                input.expectedDeviceVersion,
                input.expectedProvisioningVersion,
                client.version,
            )
            .response()
            .toResponse()

    @PostMapping(consumes = ["application/json"])
    fun register(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: RegisterCollaborationDeviceRequest,
    ): MutationResponse =
        register
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                operation,
                input.platform,
                input.label,
                client.version,
            )
            .response()
            .toResponse()
}
