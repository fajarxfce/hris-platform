package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyGateway
import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyRuntimeOwner
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface CollaborationLegacyGatewayRepository {
    /** Absence is distinct from malformed or wrong-parent metadata. Caller owns admission. */
    fun find(
        spaceId: UUID,
        owner: CollaborationLegacyRuntimeOwner,
    ): Result<CollaborationLegacyGateway?>
}
