package dev.fajar.hris.core.http

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class JsonRequestLimitFilter(private val json: ObjectMapper) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val media = request.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (
            !request.requestURI.startsWith("/api/v1/") ||
                (media != "application/json" && !media.endsWith("+json"))
        ) {
            chain.doFilter(request, response)
            return
        }
        if (request.contentLengthLong > MAXIMUM_BYTES) {
            response.status = 413
            response.contentType = "application/problem+json"
            json.writeValue(
                response.outputStream,
                mapOf(
                    "status" to 413,
                    "code" to "request_body_too_large",
                    "correlationId" to request.getAttribute("hris.correlationId")?.toString(),
                ),
            )
            return
        }
        chain.doFilter(BoundedBodyRequest(request, MAXIMUM_BYTES), response)
    }

    companion object {
        const val MAXIMUM_BYTES = 1_048_576L
    }
}
