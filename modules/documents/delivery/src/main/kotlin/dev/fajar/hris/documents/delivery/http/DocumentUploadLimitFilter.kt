package dev.fajar.hris.documents.delivery.http

import dev.fajar.hris.core.http.BoundedBodyRequest
import dev.fajar.hris.documents.domain.policies.DOCUMENT_CHUNK_BYTES
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import java.util.concurrent.Semaphore
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class DocumentUploadLimitFilter(private val json: ObjectMapper) : OncePerRequestFilter() {
    private val capacity = Semaphore(4)
    private val path = Regex("/api/v1/companies/[^/]+/documents/revisions/[^/]+/chunks")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (request.method != "POST" || !path.matches(request.requestURI)) {
            chain.doFilter(request, response)
            return
        }
        if (request.contentLengthLong > DOCUMENT_CHUNK_BYTES) {
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
        if (!capacity.tryAcquire()) {
            response.status = 429
            response.contentType = "application/problem+json"
            response.setHeader("Retry-After", "1")
            json.writeValue(
                response.outputStream,
                mapOf(
                    "status" to 429,
                    "code" to "document_upload_busy",
                    "correlationId" to request.getAttribute("hris.correlationId")?.toString(),
                ),
            )
            return
        }
        try {
            chain.doFilter(
                BoundedBodyRequest(request, DOCUMENT_CHUNK_BYTES.toLong(), Duration.ofSeconds(30)),
                response,
            )
        } finally {
            capacity.release()
        }
    }
}
