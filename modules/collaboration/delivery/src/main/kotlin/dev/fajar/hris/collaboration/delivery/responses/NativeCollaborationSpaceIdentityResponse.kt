package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceIdentity
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceIdentityOrigin
import java.time.Instant
import java.util.UUID

/** Provider-user metadata only. The preferred original device is not this client's device. */
data class NativeCollaborationSpaceIdentityResponse(val identity: NativeCollaborationSpaceIdentity?)

data class NativeCollaborationSpaceIdentity(
    val spaceId: UUID,
    val accountId: UUID,
    val serverName: String,
    val matrixUserId: String,
    val origin: CollaborationSpaceIdentityOrigin,
    val legacyCompanyId: UUID?,
    val version: Long,
    val createdAt: Instant,
) {
    constructor(
        value: CollaborationSpaceIdentity
    ) : this(
        value.spaceId,
        value.accountId,
        value.serverName,
        value.matrixUserId,
        value.origin,
        value.legacy?.companyId,
        value.version,
        value.createdAt,
    )

    override fun toString() = "NativeCollaborationSpaceIdentity(<metadata>)"
}
