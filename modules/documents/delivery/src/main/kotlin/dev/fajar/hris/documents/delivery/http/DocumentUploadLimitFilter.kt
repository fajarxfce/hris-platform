package dev.fajar.hris.documents.delivery.http

import dev.fajar.hris.core.http.BoundedBodyRequest
import dev.fajar.hris.core.http.writeApiProblem
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
            writeApiProblem(
                request,
                response,
                json,
                org.springframework.http.HttpStatus.CONTENT_TOO_LARGE,
                "request_body_too_large",
                parameters = mapOf("maximumBytes" to DOCUMENT_CHUNK_BYTES.toString()),
            )
            return
        }
        if (!capacity.tryAcquire()) {
            writeApiProblem(
                request,
                response,
                json,
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "document_upload_busy",
                retryAfterSeconds = 1,
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
