package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.responses.NativeSessionResponse
import dev.fajar.hris.identity.domain.usecases.ListAccountNativeSessions
import dev.fajar.hris.identity.domain.usecases.RevokeAccountNativeSession
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/identity/accounts/{accountId}/native-sessions")
class AccountNativeSessionController(
    private val list: ListAccountNativeSessions,
    private val revoke: RevokeAccountNativeSession,
) {
    @GetMapping
    fun list(actor: Actor, @PathVariable accountId: UUID): List<NativeSessionResponse> =
        list.execute(actor, accountId).response().map { it.toResponse() }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(actor: Actor, @PathVariable accountId: UUID, @PathVariable sessionId: UUID) {
        revoke.execute(actor, accountId, sessionId).response()
    }
}
