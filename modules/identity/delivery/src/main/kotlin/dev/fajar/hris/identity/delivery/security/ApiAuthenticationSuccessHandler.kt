package dev.fajar.hris.identity.delivery.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import tools.jackson.databind.ObjectMapper

class ApiAuthenticationSuccessHandler(private val json: ObjectMapper) :
    AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val identity = authentication.principal as SessionIdentity
        response.contentType = "application/json"
        response.setHeader("Cache-Control", "no-store")
        json.writeValue(response.outputStream, mapOf("mfaConfigured" to identity.mfaConfigured))
    }
}
