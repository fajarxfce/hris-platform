package dev.fajar.hris.identity.delivery.oidc

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.Semaphore
import org.springframework.web.filter.OncePerRequestFilter

class OidcCallbackLimitFilter(private val json: tools.jackson.databind.ObjectMapper) :
    OncePerRequestFilter() {
    private val capacity = Semaphore(8)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.servletPath != "/login/oauth2/code/company"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (!capacity.tryAcquire()) {
            dev.fajar.hris.core.http.writeApiProblem(
                request,
                response,
                json,
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "sign_in_capacity_exceeded",
                retryAfterSeconds = 1,
            )
            return
        }
        try {
            chain.doFilter(request, response)
        } finally {
            capacity.release()
        }
    }
}
