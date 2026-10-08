package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.identity.delivery.responses.IdentityProviderResponse
import dev.fajar.hris.identity.domain.entities.OidcPolicy
import org.springframework.web.bind.annotation.*

@RestController
class IdentityProviderController(private val policy: OidcPolicy) {
    @GetMapping("/api/v1/auth/providers")
    fun providers(): List<IdentityProviderResponse> =
        if (policy.enabled)
            listOf(
                IdentityProviderResponse("company", "Company SSO", "/oauth2/authorization/company")
            )
        else emptyList()
}
