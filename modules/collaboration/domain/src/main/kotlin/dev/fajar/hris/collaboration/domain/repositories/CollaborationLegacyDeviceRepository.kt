package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID

/** Caller admits the native account and reads under its transaction-local context. */
interface CollaborationLegacyDeviceRepository {
    fun list(
        spaceId: UUID,
        accountId: UUID,
        after: CollaborationLegacyDevicePosition?,
        limit: Int,
    ): Result<CollaborationLegacyDevicePage>
}
