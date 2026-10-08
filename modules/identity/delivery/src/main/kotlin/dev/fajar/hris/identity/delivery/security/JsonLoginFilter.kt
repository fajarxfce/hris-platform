package dev.fajar.hris.identity.delivery.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

class JsonLoginFilter(manager: AuthenticationManager, private val converter: JsonLoginConverter) :
    AbstractAuthenticationProcessingFilter(
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/login"),
        manager,
    ) {
    override fun attemptAuthentication(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): Authentication = authenticationManager.authenticate(converter.convert(request))
}
