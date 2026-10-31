package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationDirectRoomBinding
import dev.fajar.hris.collaboration.domain.entities.CollaborationDirectRoomProvisioning
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface CollaborationDirectRoomRepository {
    /** Lease (worker) or operation (HTTP), room, policy/company/member/account, parent/device. */
    fun lock(companyId: UUID, conversationId: UUID): Result<Unit>

    fun provisioning(
        companyId: UUID,
        conversationId: UUID,
    ): Result<CollaborationDirectRoomProvisioning?>

    fun binding(companyId: UUID, conversationId: UUID): Result<CollaborationDirectRoomBinding?>

    /** Same transaction as creating the immutable job and command receipt. */
    fun create(value: CollaborationDirectRoomProvisioning): Result<Unit>

    fun replace(value: CollaborationDirectRoomProvisioning, expectedVersion: Long): Result<Unit>

    /** Caller owns current authority, room and lease fences after provider I/O. */
    fun publish(value: CollaborationDirectRoomBinding): Result<Unit>
}
