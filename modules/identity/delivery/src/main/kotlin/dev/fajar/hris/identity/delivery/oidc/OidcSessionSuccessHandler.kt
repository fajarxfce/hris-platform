package dev.fajar.hris.identity.delivery.oidc

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

class OidcSessionSuccessHandler(private val contexts: HttpSessionSecurityContextRepository) :
    AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val identity =
            authentication.principal as dev.fajar.hris.identity.delivery.security.SessionIdentity
        val context =
            SecurityContextHolder.createEmptyContext().apply {
                this.authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                        identity,
                        null,
                        listOf(SimpleGrantedAuthority("SESSION")),
                    )
            }
        SecurityContextHolder.setContext(context)
        contexts.saveContext(context, request, response)
        response.setHeader("Cache-Control", "no-store")
        response.status = 302
        response.setHeader("Location", "/auth/complete")
    }
}
