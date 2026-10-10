package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.requests.MfaConfirmationRequest
import dev.fajar.hris.identity.delivery.responses.MfaEnrollmentResponse
import dev.fajar.hris.identity.delivery.responses.NativeMfaResponse
import dev.fajar.hris.identity.delivery.security.*
import dev.fajar.hris.identity.domain.usecases.BeginMfaEnrollment
import dev.fajar.hris.identity.domain.usecases.ConfirmMfaEnrollment
import dev.fajar.hris.identity.domain.usecases.ElevateNativeSession
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/auth/native/mfa/enrollment")
class NativeMfaEnrollmentController(
    private val begin: BeginMfaEnrollment,
    private val confirm: ConfirmMfaEnrollment,
    private val elevate: ElevateNativeSession,
) {
    @PendingMfaAllowed
    @PostMapping
    fun begin(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        response: HttpServletResponse,
    ): MfaEnrollmentResponse {
        response.setHeader("Cache-Control", "no-store")
        return begin.execute(actor, operationId).response().toResponse()
    }

    @PendingMfaAllowed
    @PostMapping("/confirm", consumes = ["application/json"])
    fun confirm(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: MfaConfirmationRequest,
    ): NativeMfaResponse {
        val verified = confirm.execute(actor, input.operationId, input.code).response()
        val credentials =
            elevate
                .execute(
                    actor,
                    identity.sessionId,
                    identity.sessionVersion,
                    verified.proof,
                    operationId,
                )
                .response()
        return NativeMfaResponse(credentials.toResponse(), verified.recoveryCodes)
    }
}
