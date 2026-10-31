package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.CollaborationPlatform
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID

data class NativeCollaborationDirectRoomRequest(
    val conversationId: UUID,
    val deviceId: UUID,
    val parentId: UUID,
    val platform: CollaborationPlatform,
    val client: ClientVersion,
)

fun nativeCollaborationDirectRoomRequest(
    request: JobRequest
): Result<NativeCollaborationDirectRoomRequest> {
    fun invalid() =
        Result.Failed(Failure(FailureKind.UNEXPECTED, "invalid_collaboration_room_provisioning"))
    if (
        request.kind != JobKind.COLLABORATION_DIRECT_PROVISION ||
            request.values.keys !=
                setOf("conversationId", "deviceId", "nativeSessionId", "platform", "clientBuild")
    )
        return invalid()
    val raw = request.values["conversationId"] ?: return invalid()
    val id =
        try {
            UUID.fromString(raw).takeIf { it.toString() == raw }
        } catch (_: IllegalArgumentException) {
            null
        } ?: return invalid()
    return nativeCollaborationProvisioningRequest(
            request.copy(
                kind = JobKind.COLLABORATION_PROVISION,
                values = request.values - "conversationId",
            )
        )
        .flatMap {
            val client = it.client ?: return@flatMap invalid()
            Result.Success(
                NativeCollaborationDirectRoomRequest(
                    id,
                    it.deviceId,
                    it.parentId,
                    it.platform,
                    client,
                )
            )
        }
}
