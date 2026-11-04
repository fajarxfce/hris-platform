package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.delivery.requests.*
import dev.fajar.hris.collaboration.delivery.responses.NativeCollaborationSpaceBootstrapResponse
import dev.fajar.hris.collaboration.delivery.responses.NativeCollaborationSpaceDeviceResponse
import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyRuntimeDrainStatus
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceProviderRenewal
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.security.*
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/collaboration/spaces/{spaceId}/native-device")
class NativeCollaborationSpaceDeviceController(
    private val register: RegisterNativeCollaborationSpaceDevice,
    private val status: GetNativeCollaborationSpaceDeviceStatus,
    private val retry: RetryNativeCollaborationSpaceProvisioning,
    private val freeze: FreezeNativeCollaborationLegacyRuntime,
    private val drain: GetNativeCollaborationLegacyRuntimeDrain,
    private val bootstrap: BootstrapNativeCollaborationSpaceGateway,
    private val renewProvider: RenewNativeCollaborationSpaceProvider,
) {
    @PostMapping("/provider/renew", consumes = ["application/json"])
    fun renewSpaceProvider(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: RenewCollaborationSpaceProviderRequest,
    ): ResponseEntity<CollaborationSpaceProviderRenewal> =
        ResponseEntity.ok()
            .header("Cache-Control", "private, no-store")
            .body(
                renewProvider
                    .execute(
                        actor,
                        identity.sessionId,
                        identity.sessionVersion,
                        spaceId,
                        operation,
                        input.deviceId,
                        input.expectedDeviceVersion,
                        client.version,
                    )
                    .response()
            )

    @PostMapping("/bootstrap", consumes = ["application/json"])
    fun bootstrapSpaceGateway(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: BootstrapCollaborationSpaceGatewayRequest,
    ): ResponseEntity<NativeCollaborationSpaceBootstrapResponse> {
        val result =
            bootstrap
                .execute(
                    actor,
                    identity.sessionId,
                    identity.sessionVersion,
                    spaceId,
                    operation,
                    input.deviceId,
                    input.expectedDeviceVersion,
                    input.expectedGatewayVersion,
                    client.version,
                )
                .response()
        return ResponseEntity.ok()
            .header("Cache-Control", "private, no-store")
            .body(NativeCollaborationSpaceBootstrapResponse(result))
    }

    @GetMapping
    fun spaceDeviceStatus(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
    ) =
        NativeCollaborationSpaceDeviceResponse(
            status
                .execute(
                    actor,
                    identity.sessionId,
                    identity.sessionVersion,
                    spaceId,
                    client.version,
                )
                .response()
        )

    @PostMapping(consumes = ["application/json"])
    fun registerSpaceDevice(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: RegisterCollaborationSpaceDeviceRequest,
    ): MutationResponse =
        register
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                operation,
                input.platform,
                input.label,
                input.origin,
                input.legacy,
                input.legacyKeyFingerprint,
            )
            .response()
            .toResponse()

    @PostMapping("/retry", consumes = ["application/json"])
    fun retrySpaceProvisioning(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: RetryCollaborationSpaceProvisioningRequest,
    ): MutationResponse =
        retry
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                operation,
                input.deviceId,
                input.expectedDeviceVersion,
                input.expectedProvisioningRevision,
            )
            .response()
            .toResponse()

    @PostMapping("/legacy-runtime/freeze", consumes = ["application/json"])
    fun freezeLegacyRuntime(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestHeader("Idempotency-Key") operation: UUID,
        @RequestBody input: FreezeCollaborationLegacyRuntimeRequest,
    ): MutationResponse =
        freeze
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                operation,
                input.deviceId,
                input.expectedDeviceVersion,
                input.fingerprint,
            )
            .response()
            .toResponse()

    @GetMapping("/legacy-runtime/drain")
    fun legacyRuntimeDrain(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @PathVariable spaceId: UUID,
        @RequestParam deviceId: UUID,
        @RequestParam expectedDeviceVersion: Long,
        @RequestParam fingerprint: String,
    ): CollaborationLegacyRuntimeDrainStatus =
        drain
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                spaceId,
                client.version,
                deviceId,
                expectedDeviceVersion,
                fingerprint,
            )
            .response()
}
