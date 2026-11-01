package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.collaboration.delivery.matrix.matrixGatewayProblem
import dev.fajar.hris.collaboration.domain.policies.collaborationGatewayAuthenticationRequired
import dev.fajar.hris.collaboration.domain.usecases.AccessNativeCollaborationMatrixDevice
import dev.fajar.hris.core.domain.Result
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Managed vendor extension: the caller supplies neither a push token nor provider configuration.
 */
@RestController
class MatrixGatewayPusherController(private val device: AccessNativeCollaborationMatrixDevice) {
    @PostMapping("/_matrix/client/unstable/dev.fajar.hris/pusher")
    fun ensure(request: HttpServletRequest): ResponseEntity<Any> {
        if (
            request.queryString != null ||
                request.contentLengthLong > 0 ||
                request.getHeader("Transfer-Encoding") != null
        )
            return ResponseEntity.badRequest()
                .header("Cache-Control", "no-store")
                .body(
                    mapOf(
                        "errcode" to "M_INVALID_PARAM",
                        "error" to "Invalid push registration request.",
                    )
                )
        val headers = request.getHeaders("Authorization")
        val first = if (headers.hasMoreElements()) headers.nextElement() else ""
        if (headers.hasMoreElements() || !first.startsWith("Bearer ", ignoreCase = true))
            return matrixGatewayProblem(collaborationGatewayAuthenticationRequired().failure)
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        return when (val result = device.ensurePusher(first.substring(7), correlation)) {
            is Result.Failed -> matrixGatewayProblem(result.failure)
            is Result.Success ->
                ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(emptyMap<String, Any>())
        }
    }
}
