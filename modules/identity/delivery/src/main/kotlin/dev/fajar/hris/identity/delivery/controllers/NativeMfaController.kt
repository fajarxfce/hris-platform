package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.requests.MfaVerificationRequest
import dev.fajar.hris.identity.delivery.responses.NativeMfaResponse
import dev.fajar.hris.identity.delivery.security.*
import dev.fajar.hris.identity.domain.usecases.*
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/auth/native/mfa")
class NativeMfaController(
    private val verify: VerifyMfa,
    private val regenerate: RegenerateMfaRecoveryCodes,
    private val elevate: ElevateNativeSession,
) {
    @PendingMfaAllowed
    @PostMapping("/verify", consumes = ["application/json"])
    fun verify(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: MfaVerificationRequest,
    ): NativeMfaResponse {
        val result = verify.execute(actor, input.code, input.recovery).response()
        return NativeMfaResponse(
            elevate
                .execute(
                    actor,
                    identity.sessionId,
                    identity.sessionVersion,
                    result.proof,
                    operationId,
                )
                .response()
                .toResponse()
        )
    }

    @PostMapping("/recovery-codes")
    fun recovery(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        @RequestHeader("Idempotency-Key") operationId: UUID,
    ): NativeMfaResponse {
        val result = regenerate.execute(actor).response()
        return NativeMfaResponse(
            elevate
                .execute(
                    actor,
                    identity.sessionId,
                    identity.sessionVersion,
                    result.proof,
                    operationId,
                )
                .response()
                .toResponse(),
            result.recoveryCodes,
        )
    }
}
