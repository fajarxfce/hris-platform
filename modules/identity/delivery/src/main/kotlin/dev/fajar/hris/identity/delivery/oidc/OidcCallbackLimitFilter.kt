package dev.fajar.hris.identity.delivery.oidc

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.Semaphore
import org.springframework.web.filter.OncePerRequestFilter

class OidcCallbackLimitFilter : OncePerRequestFilter() {
    private val capacity = Semaphore(8)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.servletPath != "/login/oauth2/code/company"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (!capacity.tryAcquire()) {
            response.status = 503
            response.contentType = "application/problem+json"
            response.setHeader("Cache-Control", "no-store")
            response.writer.write("{\"status\":503,\"code\":\"sign_in_capacity_exceeded\"}")
            return
        }
        try {
            chain.doFilter(request, response)
        } finally {
            capacity.release()
        }
    }
}
