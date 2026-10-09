package dev.fajar.hris.identity.delivery.security

import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.http.writeApiProblem
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
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
                FailureKind.RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS
                FailureKind.UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE
                FailureKind.UNEXPECTED -> HttpStatus.INTERNAL_SERVER_ERROR
                FailureKind.VALIDATION -> HttpStatus.BAD_REQUEST
                else -> HttpStatus.UNAUTHORIZED
            }
        writeApiProblem(
            request,
            response,
            json,
            status,
            failure?.code ?: "authentication_required",
            failure?.fields.orEmpty(),
            failure?.parameters.orEmpty(),
            retryAfterSeconds = if (status == HttpStatus.TOO_MANY_REQUESTS) 900 else null,
        )
    }
}
