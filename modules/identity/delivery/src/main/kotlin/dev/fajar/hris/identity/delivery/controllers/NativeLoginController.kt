package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.requests.NativeLoginRequest
import dev.fajar.hris.identity.delivery.responses.AccountResponse
import dev.fajar.hris.identity.delivery.responses.NativeLoginResponse
import dev.fajar.hris.identity.domain.entities.NativeSessionPurpose
import dev.fajar.hris.identity.domain.usecases.IssueNativeSession
import dev.fajar.hris.identity.domain.usecases.SignInWithPassword
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Clock
import java.util.UUID
import org.springframework.web.bind.annotation.*

/** Establishes native credentials after the shared password authentication boundary. */
@RestController
@RequestMapping("/api/v1/auth/native")
class NativeLoginController(
    private val signIn: SignInWithPassword,
    private val issue: IssueNativeSession,
    private val clock: Clock,
) {
    @PostMapping("/login", consumes = ["application/json"])
    fun login(
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: NativeLoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): NativeLoginResponse {
        response.setHeader("Cache-Control", "no-store")
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        val account =
            signIn.execute(input.email, input.password, correlation, request.remoteAddr).response()
        val actor =
            Actor(
                account.id,
                null,
                emptySet(),
                clock.instant(),
                correlation,
                credentialVersion = account.securityVersion,
            )
        val credentials =
            issue
                .execute(
                    actor,
                    operationId,
                    input.deviceName,
                    NativeSessionPurpose.PASSWORD_SIGN_IN,
                )
                .response()
        return NativeLoginResponse(
            AccountResponse(
                account.id.toString(),
                account.email,
                account.displayName,
                account.mfaConfigured,
            ),
            credentials.toResponse(),
        )
    }
}
