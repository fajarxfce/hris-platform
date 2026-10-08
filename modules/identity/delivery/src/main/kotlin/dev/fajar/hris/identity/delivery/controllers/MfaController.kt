package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.requests.*
import dev.fajar.hris.identity.delivery.responses.*
import dev.fajar.hris.identity.delivery.security.*
import dev.fajar.hris.identity.domain.usecases.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.COOKIE)
@RequestMapping("/api/v1/auth/mfa")
class MfaController(
    private val begin: BeginMfaEnrollment,
    private val confirm: ConfirmMfaEnrollment,
    private val verify: VerifyMfa,
    private val regenerate: RegenerateMfaRecoveryCodes,
    private val elevation: MfaSessionElevation,
) {
    @PendingMfaAllowed
    @PostMapping("/enrollment")
    fun enrollment(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        response: HttpServletResponse,
    ): MfaEnrollmentResponse {
        response.setHeader("Cache-Control", "no-store")
        return begin.execute(actor, operationId).response().toResponse()
    }

    @PendingMfaAllowed
    @PostMapping("/enrollment/confirm")
    fun confirm(
        actor: Actor,
        @RequestBody input: MfaConfirmationRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): MfaVerificationResponse {
        val result = confirm.execute(actor, input.operationId, input.code).response()
        elevation.elevate(result.proof, request, response)
        return MfaVerificationResponse(result.proof.verifiedAt.toString(), result.recoveryCodes)
    }

    @PendingMfaAllowed
    @PostMapping("/verify")
    fun verify(
        actor: Actor,
        @RequestBody input: MfaVerificationRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): MfaVerificationResponse {
        val result = verify.execute(actor, input.code, input.recovery).response()
        elevation.elevate(result.proof, request, response)
        return MfaVerificationResponse(result.proof.verifiedAt.toString(), result.recoveryCodes)
    }

    @PostMapping("/recovery-codes")
    fun recovery(
        actor: Actor,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): MfaVerificationResponse {
        val result = regenerate.execute(actor).response()
        elevation.elevate(result.proof, request, response)
        return MfaVerificationResponse(result.proof.verifiedAt.toString(), result.recoveryCodes)
    }
}
