package dev.fajar.hris.identity.delivery.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import tools.jackson.databind.ObjectMapper

class ApiAuthenticationFailureHandler(private val json: ObjectMapper) :
    AuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        error: AuthenticationException,
    ) {
        val failure = (error as? IdentityAuthenticationException)?.failure
        val status =
            when (failure?.kind) {
                dev.fajar.hris.core.domain.FailureKind.RATE_LIMITED -> 429
                dev.fajar.hris.core.domain.FailureKind.UNAVAILABLE -> 503
                dev.fajar.hris.core.domain.FailureKind.UNEXPECTED -> 500
                dev.fajar.hris.core.domain.FailureKind.VALIDATION -> 400
                else -> 401
            }
        if (status == 429) response.setHeader("Retry-After", "900")
        response.status = status
        response.contentType = "application/problem+json"
        json.writeValue(
            response.outputStream,
            mapOf(
                "status" to status,
                "code" to (failure?.code ?: "authentication_required"),
                "correlationId" to request.getAttribute("hris.correlationId")?.toString(),
            ),
        )
    }
}
