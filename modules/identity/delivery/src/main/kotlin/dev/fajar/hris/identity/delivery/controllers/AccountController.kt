package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.mappers.toResponse
import dev.fajar.hris.identity.delivery.responses.CompanyAccessResponse
import dev.fajar.hris.identity.delivery.responses.CsrfResponse
import dev.fajar.hris.identity.delivery.responses.SessionResponse
import dev.fajar.hris.identity.domain.usecases.GetCurrentAccount
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AccountController(private val current: GetCurrentAccount) {
    @GetMapping("/api/v1/auth/csrf")
    fun csrf(token: CsrfToken): CsrfResponse = CsrfResponse(token.token, token.headerName)

    @dev.fajar.hris.identity.delivery.security.PendingMfaAllowed
    @GetMapping("/api/v1/me")
    fun me(actor: Actor): SessionResponse = current.execute(actor).response().toResponse()

    @GetMapping("/api/v1/companies/{companyId}/me/access")
    fun access(actor: Actor): CompanyAccessResponse =
        CompanyAccessResponse(requireNotNull(actor.companyId), actor.permissions.sorted())
}
