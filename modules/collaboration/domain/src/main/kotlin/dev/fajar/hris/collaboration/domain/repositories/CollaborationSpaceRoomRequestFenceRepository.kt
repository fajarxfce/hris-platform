package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomRequestFence
import dev.fajar.hris.core.domain.Result
import java.util.UUID

/** Exact retained facts. The owning use case holds the original operation lock and admission. */
interface CollaborationSpaceRoomRequestFenceRepository {
    fun find(accountId: UUID, operationId: UUID): Result<CollaborationSpaceRoomRequestFence?>

    fun record(value: CollaborationSpaceRoomRequestFence): Result<Unit>
}
