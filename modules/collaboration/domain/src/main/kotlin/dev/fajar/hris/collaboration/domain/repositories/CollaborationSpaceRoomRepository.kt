package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID

/** Caller owns current admission, guard order, receipt/journal and transaction boundaries. */
interface CollaborationSpaceRoomRepository {
    /** Original unsigned pair guard must precede account/native guards. */
    fun lockOriginalPair(companyId: UUID, first: UUID, second: UUID): Result<Unit>

    fun lock(spaceId: UUID, conversationId: UUID): Result<Unit>

    fun intent(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): Result<CollaborationSpaceRoomIntent?>

    fun createIntent(value: CollaborationSpaceRoomIntent): Result<Unit>

    fun binding(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): Result<CollaborationSpaceRoomBinding?>

    fun status(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): Result<CollaborationSpaceRoomStatus?>

    fun provisioning(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
        lock: Boolean = false,
    ): Result<CollaborationSpaceRoomProvisioning?>

    fun enqueue(request: CollaborationSpaceRoomRequest): Result<Unit>

    fun retry(request: CollaborationSpaceRoomRequest, expectedRevision: Long): Result<Unit>

    /** Worker-only bounded claim transaction; commit before acquiring application guards. */
    fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
    ): Result<List<CollaborationSpaceRoomProvisioning>>

    /** Same transaction as publish/receipt/journal. False requires rollback of that transaction. */
    fun finish(
        lease: CollaborationSpaceRoomLease,
        failure: CollaborationSpaceRoomFailure? = null,
    ): Result<Boolean>

    fun defer(lease: CollaborationSpaceRoomLease, delaySeconds: Int): Result<Boolean>

    fun cancel(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
        expectedRevision: Long,
    ): Result<Boolean>

    /**
     * Exact lease/current authority was checked by caller. Binding + success must commit together.
     */
    fun publish(value: CollaborationSpaceRoomBinding): Result<Unit>
}
