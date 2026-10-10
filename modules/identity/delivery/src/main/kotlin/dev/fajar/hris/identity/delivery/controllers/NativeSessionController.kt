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
@RequestMapping("/api/v1/auth/native")
class NativeSessionController(
    private val exchange: IssueNativeSession,
    private val refresh: RefreshNativeSession,
    private val list: ListNativeSessions,
    private val revoke: RevokeNativeSession,
) {
    @SessionTransport(SessionKind.COOKIE)
    @PostMapping("/exchange", consumes = ["application/json"])
    fun exchange(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: NativeExchangeRequest,
        response: HttpServletResponse,
    ): NativeTokensResponse {
        response.setHeader("Cache-Control", "no-store")
        return exchange.execute(actor, operationId, input.deviceName).response().toResponse()
    }

    @PostMapping("/refresh", consumes = ["application/json"])
    fun refresh(
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: NativeRefreshRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): NativeTokensResponse {
        response.setHeader("Cache-Control", "no-store")
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        return refresh.execute(input.refreshToken, operationId, correlation).response().toResponse()
    }

    @PendingMfaAllowed
    @GetMapping("/sessions")
    fun sessions(actor: Actor): List<NativeSessionResponse> =
        list.execute(actor).response().map { it.toResponse() }

    @PendingMfaAllowed
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    fun revoke(actor: Actor, @PathVariable sessionId: UUID) {
        revoke.execute(actor, sessionId).response()
    }
}
