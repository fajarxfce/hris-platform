package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.Failure
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import tools.jackson.databind.ObjectMapper

/** HTTP transport contract. Codes and parameters are data for client-side translation. */
fun apiProblem(
    status: HttpStatusCode,
    code: String,
    correlationId: String?,
    fields: Map<String, String> = emptyMap(),
    parameters: Map<String, String> = emptyMap(),
    retryAfterSeconds: Long? = null,
): ProblemDetail =
    ProblemDetail.forStatus(status).apply {
        type = URI.create("urn:hris:problem:$code")
        setProperty("code", code)
        setProperty("fields", fields)
        setProperty("parameters", parameters)
        setProperty("correlationId", correlationId)
        if (retryAfterSeconds != null) setProperty("retryAfterSeconds", retryAfterSeconds)
    }

fun apiProblem(failure: Failure, correlationId: String?): ProblemDetail =
    apiProblem(
        failureHttpStatus(failure.kind),
        failure.code,
        correlationId,
        failure.fields,
        failure.parameters,
    )

/** Filters run outside MVC's ProblemDetail converter; keep the same wire representation. */
fun writeApiProblem(
    request: HttpServletRequest,
    response: HttpServletResponse,
    json: ObjectMapper,
    status: HttpStatusCode,
    code: String,
    fields: Map<String, String> = emptyMap(),
    parameters: Map<String, String> = emptyMap(),
    retryAfterSeconds: Long? = null,
) {
    val problem =
        apiProblem(
            status,
            code,
            request.getAttribute("hris.correlationId")?.toString(),
            fields,
            parameters,
            retryAfterSeconds,
        )
    response.status = status.value()
    response.contentType = "application/problem+json"
    response.setHeader("Cache-Control", "no-store")
    if (retryAfterSeconds != null) response.setHeader("Retry-After", retryAfterSeconds.toString())
    json.writeValue(
        response.outputStream,
        buildMap {
            put("type", problem.type.toString())
            put("title", problem.title)
            put("status", problem.status)
            putAll(requireNotNull(problem.properties))
        },
    )
}
