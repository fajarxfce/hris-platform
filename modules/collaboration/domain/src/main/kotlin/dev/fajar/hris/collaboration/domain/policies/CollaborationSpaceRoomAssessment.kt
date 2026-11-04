package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.util.UUID

/** Actual provider creator after reconciliation, independent of the initial HRIS requester. */
data class CollaborationSpaceRoomAssessment(
    val creatorAccountId: UUID,
    val repair: CollaborationMatrixRoomRepair,
)

/**
 * The request is oriented to the current caller's own provider identity. A stable alias may have
 * been created by its exact canonical peer before a lost response. This policy grants no join or
 * native/device authority; the worker admits those.
 */
fun assessCollaborationSpaceRecoveredRoom(
    caller: CollaborationSpaceMatrixDirectRoom,
    observed: CollaborationSpaceMatrixRoomObservation,
    serverName: String,
): Result<CollaborationSpaceRoomAssessment> {
    if (
        !validCollaborationSpaceRoomRequest(caller, serverName) ||
            observed.members[caller.creatorUserId] != "join"
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_mismatch"))
    val actual =
        when (observed.creator) {
            caller.creatorUserId -> caller
            caller.peerUserId ->
                caller.copy(
                    creatorAccountId = caller.peerAccountId,
                    peerAccountId = caller.creatorAccountId,
                    creatorUserId = caller.peerUserId,
                    peerUserId = caller.creatorUserId,
                )
            else ->
                return Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_mismatch"))
        }
    return assessCollaborationSpaceDirectRoom(actual, observed, serverName).flatMap { repair ->
        if (
            actual.creatorAccountId != caller.creatorAccountId &&
                (repair.missingSettings.isNotEmpty() || repair.missingInvite)
        ) {
            Result.Failed(
                Failure(FailureKind.CONFLICT, "collaboration_room_recovery_requires_creator")
            )
        } else Result.Success(CollaborationSpaceRoomAssessment(actual.creatorAccountId, repair))
    }
}
