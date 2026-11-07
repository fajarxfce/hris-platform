package dev.fajar.hris.collaboration.delivery.matrix

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import tools.jackson.databind.ObjectMapper

private val gatewayRejectionLogger = LoggerFactory.getLogger("dev.fajar.hris.collaboration.gateway")

private val gatewayRejectionReasons =
    setOf(
        "client_update_required",
        "client_version_required",
        "collaboration_access_denied",
        "collaboration_direct_metadata_unavailable",
        "collaboration_peer_unavailable",
        "collaboration_recipient_unavailable",
        "collaboration_room_creator_required",
        "company_access_denied",
        "company_module_disabled",
        "company_required",
        "mfa_required",
        "mfa_setup_required",
        "platform_scope_required",
        "recent_authentication_required",
    )

fun matrixGatewayProblem(failure: Failure): ResponseEntity<Any> {
    val (status, code) =
        when (failure.kind) {
            FailureKind.UNAUTHENTICATED -> 401 to "M_UNKNOWN_TOKEN"
            FailureKind.FORBIDDEN -> 403 to "M_FORBIDDEN"
            FailureKind.NOT_FOUND -> 404 to "M_NOT_FOUND"
            FailureKind.RATE_LIMITED -> 429 to "M_LIMIT_EXCEEDED"
            FailureKind.VALIDATION -> 400 to "M_BAD_JSON"
            FailureKind.CONFLICT -> 409 to "M_UNKNOWN"
            FailureKind.UNAVAILABLE -> 503 to "M_UNKNOWN"
            else -> 500 to "M_UNKNOWN"
        }
    if (status == 403)
        gatewayRejectionLogger.warn(
            "MatrixGatewayRejected status=403 reason={}",
            failure.code.takeIf { it in gatewayRejectionReasons } ?: "other",
        )
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
    gatewayRejectionLogger.warn("MatrixGatewayRejected status=403 reason=endpoint_unavailable")
    response.status = 403
    response.contentType = "application/json"
    response.setHeader("Cache-Control", "no-store")
    json.writeValue(
        response.outputStream,
        mapOf("errcode" to "M_FORBIDDEN", "error" to "This Matrix endpoint is unavailable."),
    )
}
