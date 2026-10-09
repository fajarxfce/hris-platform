package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.requests.*
import dev.fajar.hris.identity.delivery.responses.NativePushRegistrationResponse
import dev.fajar.hris.identity.delivery.security.*
import dev.fajar.hris.identity.domain.entities.SaveNativePushRegistrationCommand
import dev.fajar.hris.identity.domain.usecases.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/auth/native/push-registration")
class NativePushRegistrationController(
    private val get: GetNativePushRegistration,
    private val save: SaveNativePushRegistration,
    private val disable: DisableNativePushRegistration,
) {
    @GetMapping
    fun get(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
    ): NativePushRegistrationResponse =
        get.execute(actor, identity.sessionId, identity.sessionVersion).response().toResponse()

    @PutMapping(consumes = ["application/json"])
    fun save(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: SaveNativePushRegistrationRequest,
    ) =
        save
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                operationId,
                SaveNativePushRegistrationCommand(
                    input.expectedVersion,
                    input.platform,
                    input.token,
                ),
            )
            .response()
            .toResponse()

    @PostMapping("/disable", consumes = ["application/json"])
    fun disable(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: DisableNativePushRegistrationRequest,
    ) =
        disable
            .execute(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                operationId,
                input.expectedVersion,
            )
            .response()
            .toResponse()
}
