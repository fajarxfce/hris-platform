package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationDirectConversation
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface CollaborationDirectRepository {
    /** Canonical pair guard precedes company/access guards. UUIDs use PostgreSQL ordering. */
    fun lockPair(companyId: UUID, first: UUID, second: UUID): Result<Unit>

    fun findPair(
        companyId: UUID,
        first: UUID,
        second: UUID,
    ): Result<CollaborationDirectConversation?>

    fun find(companyId: UUID, id: UUID): Result<CollaborationDirectConversation?>

    fun list(
        companyId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationDirectConversation>>

    fun create(conversation: CollaborationDirectConversation): Result<Unit>
}
