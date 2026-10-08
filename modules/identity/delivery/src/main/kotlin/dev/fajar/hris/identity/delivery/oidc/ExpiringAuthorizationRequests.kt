package dev.fajar.hris.identity.delivery.oidc

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Clock
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest

class ExpiringAuthorizationRequests(private val clock: Clock) :
    AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private val delegate = HttpSessionOAuth2AuthorizationRequestRepository()

    override fun loadAuthorizationRequest(
        request: HttpServletRequest
    ): OAuth2AuthorizationRequest? = delegate.loadAuthorizationRequest(request)?.takeIf(::unexpired)

    override fun removeAuthorizationRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): OAuth2AuthorizationRequest? =
        delegate.removeAuthorizationRequest(request, response)?.takeIf(::unexpired)

    override fun saveAuthorizationRequest(
        value: OAuth2AuthorizationRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val stored =
            value.let {
                OAuth2AuthorizationRequest.from(it)
                    .attributes { attrs -> attrs["hris.createdAt"] = clock.instant().epochSecond }
                    .build()
            }
        delegate.saveAuthorizationRequest(stored, request, response)
    }

    private fun unexpired(request: OAuth2AuthorizationRequest): Boolean {
        val created = request.getAttribute<Long>("hris.createdAt") ?: return false
        return clock.instant().epochSecond - created in 0 until 300
    }
}
