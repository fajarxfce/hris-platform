package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationLegacyDeviceDataSource
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixDeviceId
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.collaboration.domain.repositories.CollaborationLegacyDeviceRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID

class StoredCollaborationLegacyDeviceRepository(
    private val source: CollaborationLegacyDeviceDataSource
) : CollaborationLegacyDeviceRepository {
    override fun list(
        spaceId: UUID,
        accountId: UUID,
        after: CollaborationLegacyDevicePosition?,
        limit: Int,
    ): Result<CollaborationLegacyDevicePage> {
        if (limit !in 1..50)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_legacy_query")
            )
        return safeDatabaseCall {
            val rows = source.list(spaceId, accountId, after?.companyId, after?.deviceId, limit + 1)
            require(rows.size <= limit + 1)
            val mapped =
                rows.map { row ->
                    require(row.spaceId == spaceId && row.accountId == accountId)
                    val company = requireNotNull(row.legacyCompanyId)
                    val device = requireNotNull(row.legacyDeviceId)
                    val user = requireNotNull(row.matrixUserId)
                    val matrixDevice = requireNotNull(row.matrixDeviceId)
                    require(
                        user.length <= 255 &&
                            user.substringBefore(':') ==
                                "@" + collaborationMatrixLocalpart(company, accountId)
                    )
                    require(
                        user.substringAfter(':', "").isNotBlank() &&
                            user.none { it.isWhitespace() || it.isISOControl() }
                    )
                    require(matrixDevice == collaborationMatrixDeviceId(device))
                    val state = CollaborationDeviceState.valueOf(requireNotNull(row.state))
                    val version = requireNotNull(row.deviceVersion)
                    val generation = requireNotNull(row.credentialGeneration)
                    val created = requireNotNull(row.deviceCreatedAt).toInstant()
                    val observed = requireNotNull(row.identityObservedAt).toInstant()
                    val revoked = row.deviceRevokedAt?.toInstant()
                    val retired = row.credentialRetiredAt?.toInstant()
                    require(
                        version in 0..1000000 && generation in 0..1000000 && observed >= created
                    )
                    require((state == CollaborationDeviceState.REVOKED) == (revoked != null))
                    require(revoked == null || revoked >= created)
                    require(
                        retired == null ||
                            (state == CollaborationDeviceState.REVOKED && retired >= observed)
                    )
                    CollaborationLegacyDevice(
                        CollaborationLegacyDevicePosition(company, device),
                        accountId,
                        user,
                        matrixDevice,
                        CollaborationPlatform.valueOf(requireNotNull(row.platform)),
                        state,
                        version,
                        generation,
                        created,
                        observed,
                        revoked,
                        retired,
                    )
                }
            fun positionKey(position: CollaborationLegacyDevicePosition) =
                position.companyId.toString() + ":" + position.deviceId.toString()
            require(
                mapped.zipWithNext().all { (a, b) ->
                    positionKey(a.position) < positionKey(b.position)
                }
            )
            require(after == null || mapped.all { positionKey(it.position) > positionKey(after) })
            CollaborationLegacyDevicePage(
                mapped.take(limit),
                if (mapped.size > limit) mapped[limit - 1].position else null,
            )
        }
    }
}
