package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface CollaborationLegacyParentRepository {
    /** Missing/unavailable metadata cannot authorize adoption or silently select a new device. */
    fun find(
        spaceId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
        position: CollaborationLegacyDevicePosition,
    ): Result<CollaborationLegacyParent?>
}
