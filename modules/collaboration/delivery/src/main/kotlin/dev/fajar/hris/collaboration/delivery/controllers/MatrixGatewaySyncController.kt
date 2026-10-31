package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.collaboration.delivery.matrix.matrixGatewayProblem
import dev.fajar.hris.collaboration.domain.entities.CollaborationSyncRequest
import dev.fajar.hris.collaboration.domain.policies.collaborationGatewayAuthenticationRequired
import dev.fajar.hris.collaboration.domain.usecases.AccessNativeCollaborationMatrixDevice
import dev.fajar.hris.core.domain.*
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MatrixGatewaySyncController(private val device: AccessNativeCollaborationMatrixDevice) {
    @GetMapping("/_matrix/client/v3/sync")
    fun get(request: HttpServletRequest): ResponseEntity<Any> {
        fun invalid() =
            matrixGatewayProblem(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_sync_request")
            )
        if (
            (request.queryString?.length ?: 0) > 8192 ||
                request.contentLengthLong > 0 ||
                request.getHeader("Transfer-Encoding") != null
        )
            return invalid()
        val headers = request.getHeaders("Authorization")
        val first = if (headers.hasMoreElements()) headers.nextElement() else ""
        if (headers.hasMoreElements() || !first.startsWith("Bearer ", ignoreCase = true))
            return matrixGatewayProblem(collaborationGatewayAuthenticationRequired().failure)
        val query = request.parameterMap
        if (
            query.keys.any {
                it !in setOf("since", "timeout", "full_state", "set_presence", "use_state_after")
            } || query.values.any { it.size != 1 }
        )
            return invalid()
        val timeoutText = request.getParameter("timeout")
        if (timeoutText != null && !timeoutText.matches(Regex("[0-9]{1,5}"))) return invalid()
        val timeout = timeoutText?.toIntOrNull() ?: 0
        if (timeout !in 0..30_000) return invalid()
        val full = request.getParameter("full_state")
        if (full != null && full !in setOf("true", "false")) return invalid()
        // The pinned SDK always sends this optional preference. We do not advertise
        // state-after support and retain our advertised v1.12 state-before response.
        val stateAfter = request.getParameter("use_state_after")
        if (stateAfter != null && stateAfter !in setOf("true", "false")) return invalid()
        val presence = request.getParameter("set_presence")
        if (presence != null && presence !in setOf("online", "offline", "unavailable"))
            return invalid()
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        return when (
            val result =
                device.sync(
                    first.substring(7),
                    correlation,
                    CollaborationSyncRequest(request.getParameter("since"), timeout, full == "true"),
                )
        ) {
            is Result.Failed -> matrixGatewayProblem(result.failure)
            is Result.Success ->
                ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(result.value.payload + ("next_batch" to result.value.nextBatch))
        }
    }
}
