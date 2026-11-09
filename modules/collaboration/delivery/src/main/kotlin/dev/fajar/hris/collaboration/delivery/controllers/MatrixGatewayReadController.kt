package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.collaboration.delivery.matrix.matrixGatewayProblem
import dev.fajar.hris.collaboration.delivery.matrix.matrixRoomHistoryFilter
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixPolicy
import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomPageRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationServicePolicy
import dev.fajar.hris.collaboration.domain.policies.collaborationGatewayAuthenticationRequired
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpaceGatewayAccess
import dev.fajar.hris.collaboration.domain.usecases.AccessNativeCollaborationMatrixDevice
import dev.fajar.hris.collaboration.domain.usecases.AccessNativeCollaborationSpaceMatrixDevice
import dev.fajar.hris.core.domain.*
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class MatrixGatewayReadController(
    private val device: AccessNativeCollaborationMatrixDevice,
    private val group: AccessNativeCollaborationSpaceMatrixDevice,
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
) {
    @GetMapping("/_matrix/client/v3/user/{userId}/account_data/m.secret_storage.default_key")
    fun secretStorageDefaultKey(
        @PathVariable userId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        read(request) { token, correlation ->
            (if (validCollaborationSpaceGatewayAccess(token))
                    group.secretStorageDefaultKey(token, correlation, userId)
                else device.secretStorageDefaultKey(token, correlation, userId))
                .map { value ->
                    value.keyId?.let { mapOf("key" to it) } ?: emptyMap<String, String>()
                }
        }

    @GetMapping("/_matrix/client/v3/user/{userId}/account_data/m.direct")
    fun directMetadata(
        @PathVariable userId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        read(request) { token, correlation ->
            (if (validCollaborationSpaceGatewayAccess(token))
                group.directMetadata(token, correlation, userId)
            else device.directMetadata(token, correlation, userId))
        }

    @GetMapping("/_matrix/client/versions")
    fun versions(request: HttpServletRequest): ResponseEntity<Any> {
        if (
            request.queryString != null ||
                request.contentLengthLong > 0 ||
                request.getHeader("Transfer-Encoding") != null
        )
            return invalid()
        if (!service.enabled || !provider.enabled)
            return matrixGatewayProblem(
                Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable")
            )
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .body(
                mapOf(
                    "versions" to listOf("v1.12"),
                    "unstable_features" to emptyMap<String, Boolean>(),
                )
            )
    }

    @GetMapping("/_matrix/client/v3/capabilities")
    fun capabilities(request: HttpServletRequest): ResponseEntity<Any> =
        read(request) { token, correlation ->
            val admitted =
                if (validCollaborationSpaceGatewayAccess(token))
                    group.capabilities(token, correlation)
                else device.capabilities(token, correlation)
            admitted.map {
                mapOf(
                    "capabilities" to
                        mapOf(
                            "m.change_password" to mapOf("enabled" to false),
                            "m.set_displayname" to mapOf("enabled" to false),
                            "m.set_avatar_url" to mapOf("enabled" to false),
                            "m.3pid_changes" to mapOf("enabled" to false),
                            "m.room_versions" to
                                mapOf("default" to "12", "available" to mapOf("12" to "stable")),
                        )
                )
            }
        }

    @GetMapping("/_matrix/client/v3/rooms/{roomId}/state")
    fun state(@PathVariable roomId: String, request: HttpServletRequest): ResponseEntity<Any> =
        read(request) { token, correlation ->
            (if (validCollaborationSpaceGatewayAccess(token))
                    group.roomState(token, correlation, roomId)
                else device.roomState(token, correlation, roomId))
                .map { it.events }
        }

    @GetMapping(
        "/_matrix/client/v3/rooms/{roomId}/state/{type}",
        "/_matrix/client/v3/rooms/{roomId}/state/{type}/",
    )
    fun stateContent(
        @PathVariable roomId: String,
        @PathVariable type: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        read(request) { token, correlation ->
            if (
                type !in
                    setOf(
                        "m.room.create",
                        "m.room.encryption",
                        "m.room.history_visibility",
                        "m.room.join_rules",
                        "m.room.guest_access",
                        "m.room.power_levels",
                    )
            )
                Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "collaboration_room_state_unavailable")
                )
            else
                (if (validCollaborationSpaceGatewayAccess(token))
                        group.roomState(token, correlation, roomId)
                    else device.roomState(token, correlation, roomId))
                    .flatMap { events ->
                        val found =
                            events.events.singleOrNull {
                                it["type"] == type && it["state_key"] == ""
                            }
                        found?.get("content")?.let { Result.Success(it) }
                            ?: Result.Failed(
                                Failure(FailureKind.NOT_FOUND, "collaboration_room_state_not_found")
                            )
                    }
        }

    @GetMapping("/_matrix/client/v3/rooms/{roomId}/members")
    fun members(@PathVariable roomId: String, request: HttpServletRequest): ResponseEntity<Any> =
        read(request, setOf("at")) { token, correlation ->
            (if (validCollaborationSpaceGatewayAccess(token))
                    group.roomMembers(token, correlation, roomId, request.getParameter("at"))
                else device.roomMembers(token, correlation, roomId, request.getParameter("at")))
                .map { mapOf("chunk" to it.events) }
        }

    @GetMapping("/_matrix/client/v3/rooms/{roomId}/messages")
    fun messages(@PathVariable roomId: String, request: HttpServletRequest): ResponseEntity<Any> =
        read(request, setOf("from", "to", "dir", "limit", "filter")) { token, correlation ->
            val filter =
                matrixRoomHistoryFilter(request.getParameter("filter"))
                    ?: return@read Result.Failed(
                        Failure(FailureKind.VALIDATION, "invalid_collaboration_history_request")
                    )
            val limit =
                limit(request, 10)
                    ?: return@read Result.Failed(
                        Failure(FailureKind.VALIDATION, "invalid_collaboration_history_request")
                    )
            val page =
                CollaborationRoomPageRequest(
                    request.getParameter("from"),
                    request.getParameter("to"),
                    request.getParameter("dir") ?: "",
                    limit,
                    filter,
                )
            (if (validCollaborationSpaceGatewayAccess(token))
                    group.roomMessages(token, correlation, roomId, page)
                else device.roomMessages(token, correlation, roomId, page))
                .map { it.payload }
        }

    @GetMapping("/_matrix/client/v3/rooms/{roomId}/context/{eventId}")
    fun context(
        @PathVariable roomId: String,
        @PathVariable eventId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        read(request, setOf("limit", "filter")) { token, correlation ->
            val filter =
                matrixRoomHistoryFilter(request.getParameter("filter"))
                    ?: return@read Result.Failed(
                        Failure(FailureKind.VALIDATION, "invalid_collaboration_history_request")
                    )
            val limit =
                limit(request, 10)
                    ?: return@read Result.Failed(
                        Failure(FailureKind.VALIDATION, "invalid_collaboration_history_request")
                    )
            (if (validCollaborationSpaceGatewayAccess(token))
                    group.roomContext(token, correlation, roomId, eventId, limit, filter)
                else device.roomContext(token, correlation, roomId, eventId, limit, filter))
                .map { it.payload }
        }

    @GetMapping("/_matrix/client/v3/rooms/{roomId}/event/{eventId}")
    fun event(
        @PathVariable roomId: String,
        @PathVariable eventId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        read(request) { token, correlation ->
            (if (validCollaborationSpaceGatewayAccess(token))
                    group.roomEvent(token, correlation, roomId, eventId)
                else device.roomEvent(token, correlation, roomId, eventId))
                .map { it.payload }
        }

    private fun limit(request: HttpServletRequest, default: Int): Int? {
        val value = request.getParameter("limit") ?: return default
        if (value.length !in 1..3 || value.any { it !in '0'..'9' }) return null
        return value.toIntOrNull()
    }

    private fun read(
        request: HttpServletRequest,
        parameters: Set<String> = emptySet(),
        operation: (String, UUID) -> Result<Any>,
    ): ResponseEntity<Any> {
        if (
            (request.queryString?.length ?: 0) > 8192 ||
                request.contentLengthLong > 0 ||
                request.getHeader("Transfer-Encoding") != null ||
                request.parameterMap.keys.any { it !in parameters } ||
                request.parameterMap.values.any { it.size != 1 }
        )
            return invalid()
        val headers = request.getHeaders("Authorization")
        val header = if (headers.hasMoreElements()) headers.nextElement() else ""
        if (headers.hasMoreElements() || !header.startsWith("Bearer ", ignoreCase = true))
            return matrixGatewayProblem(collaborationGatewayAuthenticationRequired().failure)
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        return when (val result = operation(header.substring(7), correlation)) {
            is Result.Failed -> matrixGatewayProblem(result.failure)
            is Result.Success ->
                ResponseEntity.ok().header("Cache-Control", "no-store").body(result.value)
        }
    }

    private fun invalid() =
        matrixGatewayProblem(Failure(FailureKind.VALIDATION, "invalid_collaboration_room_request"))
}
