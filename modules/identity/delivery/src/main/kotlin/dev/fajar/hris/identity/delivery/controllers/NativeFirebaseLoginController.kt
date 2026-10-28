package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.requests.NativeFirebaseLoginRequest
import dev.fajar.hris.identity.delivery.responses.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.usecases.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth/native/firebase")
class NativeFirebaseLoginController(
    private val verify: VerifyFirebaseSignIn,
    private val resolve: SignInWithOidc,
    private val issue: IssueNativeSession,
    private val policy: FirebaseSignInPolicy,
) {
    @GetMapping
    fun configuration(response: HttpServletResponse): NativeFirebaseConfigurationResponse {
        response.setHeader("Cache-Control", "no-store")
        return NativeFirebaseConfigurationResponse(
            policy.enabled,
            policy.projectId.takeIf { policy.enabled },
            if (policy.enabled) policy.signInProviders else emptyList(),
        )
    }

    @PostMapping("/login", consumes = ["application/json"])
    fun login(
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: NativeFirebaseLoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): NativeLoginResponse {
        response.setHeader("Cache-Control", "no-store")
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        val identity = verify.execute(input.idToken, request.remoteAddr, correlation).response()
        val account = resolve.execute(identity.issuer, identity.subject, correlation).response()
        val actor =
            Actor(
                account.id,
                null,
                emptySet(),
                identity.authenticatedAt,
                correlation,
                credentialVersion = account.securityVersion,
            )
        val session =
            issue
                .execute(
                    actor,
                    operationId,
                    input.deviceName,
                    NativeSessionPurpose.FIREBASE_SIGN_IN,
                )
                .response()
        return NativeLoginResponse(
            AccountResponse(
                account.id.toString(),
                account.email,
                account.displayName,
                account.mfaConfigured,
            ),
            session.toResponse(),
        )
    }
}
