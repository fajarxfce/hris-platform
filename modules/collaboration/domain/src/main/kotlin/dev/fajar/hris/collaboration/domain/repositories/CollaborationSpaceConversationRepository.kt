package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import java.util.UUID

/** Caller owns current native/group admission, transactions and pair/account/native guards. */
interface CollaborationSpaceConversationRepository {
    fun member(spaceId: UUID, accountId: UUID): Result<CollaborationSpaceMember?>

    fun lockPair(spaceId: UUID, first: UUID, second: UUID): Result<Unit>

    fun findPair(
        spaceId: UUID,
        first: UUID,
        second: UUID,
    ): Result<CollaborationSpaceDirectConversation?>

    fun find(
        spaceId: UUID,
        accountId: UUID,
        id: UUID,
    ): Result<CollaborationSpaceDirectConversation?>

    fun list(
        spaceId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationSpaceDirectConversation>>

    fun create(value: CollaborationSpaceDirectConversation): Result<Unit>

    fun legacy(
        spaceId: UUID,
        accountId: UUID,
        after: CollaborationLegacyConversationPosition?,
        limit: Int,
    ): Result<CollaborationSpaceLegacyConversationPage>

    /** Exact original pair inventory; no provider adoption or unbounded page scan. */
    fun legacyPair(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        first: UUID,
        second: UUID,
    ): Result<CollaborationSpaceLegacyConversation?>

    fun legacyOne(
        spaceId: UUID,
        accountId: UUID,
        position: CollaborationLegacyConversationPosition,
    ): Result<CollaborationSpaceLegacyConversation?>
}
