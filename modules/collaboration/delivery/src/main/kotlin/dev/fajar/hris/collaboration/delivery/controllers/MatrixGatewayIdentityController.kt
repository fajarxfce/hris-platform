package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.collaboration.delivery.matrix.matrixGatewayProblem
import dev.fajar.hris.collaboration.domain.policies.collaborationGatewayAuthenticationRequired
import dev.fajar.hris.collaboration.domain.usecases.GetNativeCollaborationMatrixIdentity
import dev.fajar.hris.core.domain.Result
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MatrixGatewayIdentityController(private val identity: GetNativeCollaborationMatrixIdentity) {
    @GetMapping("/_matrix/client/v3/account/whoami")
    fun get(request: HttpServletRequest): ResponseEntity<Any> {
        // Never accept tokens through URLs, cookies or a JSON body. Duplicate bearer headers
        // are ambiguous even when a servlet container would select only one of them.
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
                        "error" to "Invalid Matrix identity request.",
                    )
                )
        val headers = request.getHeaders("Authorization")
        val first = if (headers.hasMoreElements()) headers.nextElement() else ""
        if (headers.hasMoreElements() || !first.startsWith("Bearer ", ignoreCase = true))
            return matrixGatewayProblem(collaborationGatewayAuthenticationRequired().failure)
        val token = first.substring(7)
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        return when (val result = identity.execute(token, correlation)) {
            is Result.Failed -> matrixGatewayProblem(result.failure)
            is Result.Success ->
                ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(
                        mapOf(
                            "user_id" to result.value.userId,
                            "device_id" to result.value.deviceId,
                            "is_guest" to result.value.isGuest,
                        )
                    )
        }
    }
}
