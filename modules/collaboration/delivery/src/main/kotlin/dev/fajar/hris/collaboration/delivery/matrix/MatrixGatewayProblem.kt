package dev.fajar.hris.collaboration.delivery.matrix

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import tools.jackson.databind.ObjectMapper

fun matrixGatewayProblem(failure: Failure): ResponseEntity<Any> {
    val (status, code) =
        when (failure.kind) {
            FailureKind.UNAUTHENTICATED -> 401 to "M_UNKNOWN_TOKEN"
            FailureKind.FORBIDDEN -> 403 to "M_FORBIDDEN"
            FailureKind.RATE_LIMITED -> 429 to "M_LIMIT_EXCEEDED"
            FailureKind.VALIDATION -> 400 to "M_BAD_JSON"
            FailureKind.CONFLICT -> 409 to "M_UNKNOWN"
            FailureKind.UNAVAILABLE -> 503 to "M_UNKNOWN"
            else -> 500 to "M_UNKNOWN"
        }
    val body =
        buildMap<String, Any> {
            put("errcode", code)
            put("error", "The collaboration request could not be completed.")
            put("org.hris.error_code", failure.code)
            if (status == 401) put("soft_logout", true)
            if (status == 429)
                put(
                    "retry_after_ms",
                    (failure.parameters["retryAfterSeconds"]?.toLongOrNull()?.coerceIn(1, 3600)
                        ?: 10) * 1000,
                )
        }
    return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body)
}

fun writeMatrixGatewayDenied(response: HttpServletResponse, json: ObjectMapper) {
    response.status = 403
    response.contentType = "application/json"
    response.setHeader("Cache-Control", "no-store")
    json.writeValue(
        response.outputStream,
        mapOf("errcode" to "M_FORBIDDEN", "error" to "This Matrix endpoint is unavailable."),
    )
}
