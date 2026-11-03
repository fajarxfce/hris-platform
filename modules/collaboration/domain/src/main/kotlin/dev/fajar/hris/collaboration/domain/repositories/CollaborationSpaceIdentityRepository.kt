package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceIdentity
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceIdentityInput
import dev.fajar.hris.core.domain.Result
import java.util.UUID

/** Caller owns current native admission, the account guard and transaction. */
interface CollaborationSpaceIdentityRepository {
    /** Eligibility, existing selection and preferred legacy identity share one SQL snapshot. */
    fun selection(
        spaceId: UUID,
        accountId: UUID,
        serverName: String,
    ): Result<CollaborationSpaceIdentityInput?>

    fun create(identity: CollaborationSpaceIdentity): Result<Unit>
}
