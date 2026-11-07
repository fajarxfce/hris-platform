package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomRequestResolution
import dev.fajar.hris.core.http.MutationResponse
import dev.fajar.hris.core.http.toResponse
import java.time.Instant
import java.util.UUID

enum class CollaborationSpaceRoomRequestResolutionState {
    RECORDED,
    FENCED,
}

/** Exactly one setup receipt or non-execution fence; neither acknowledges an SDK message. */
class NativeCollaborationSpaceRoomRequestResolutionResponse(
    resolution: CollaborationSpaceRoomRequestResolution
) {
    val accountId = resolution.accountId
    val original = OriginalCollaborationSpaceRoomRequestIntentResponse(resolution.original)
    val state: CollaborationSpaceRoomRequestResolutionState
    val receipt: MutationResponse?
    val fence: CollaborationSpaceRoomRequestFenceResponse?

    init {
        when (resolution) {
            is CollaborationSpaceRoomRequestResolution.Recorded -> {
                state = CollaborationSpaceRoomRequestResolutionState.RECORDED
                receipt = resolution.receipt.toResponse()
                fence = null
            }
            is CollaborationSpaceRoomRequestResolution.Fenced -> {
                state = CollaborationSpaceRoomRequestResolutionState.FENCED
                receipt = null
                fence =
                    CollaborationSpaceRoomRequestFenceResponse(
                        resolution.evidence.id,
                        resolution.evidence.createdAt,
                    )
            }
        }
    }
}

data class CollaborationSpaceRoomRequestFenceResponse(val id: UUID, val createdAt: Instant)
