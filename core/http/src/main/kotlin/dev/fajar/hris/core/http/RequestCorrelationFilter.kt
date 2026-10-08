package dev.fajar.hris.core.http

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestCorrelationFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val supplied = request.getHeader("X-Request-ID")
        val id =
            if (supplied != null && supplied.length == 36) {
                runCatching { UUID.fromString(supplied) }.getOrNull() ?: UUID.randomUUID()
            } else UUID.randomUUID()
        request.setAttribute("hris.correlationId", id)
        response.setHeader("X-Request-ID", id.toString())
        MDC.putCloseable("correlationId", id.toString()).use { chain.doFilter(request, response) }
    }
}
