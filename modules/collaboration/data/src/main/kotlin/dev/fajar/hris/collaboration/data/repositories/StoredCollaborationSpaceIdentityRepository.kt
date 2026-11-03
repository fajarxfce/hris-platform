package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceIdentityDataSource
import dev.fajar.hris.collaboration.data.mappers.legacyDeviceFromRow
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceIdentityRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceIdentitiesRecord
import java.time.ZoneOffset
import java.util.UUID

class StoredCollaborationSpaceIdentityRepository(
    private val source: CollaborationSpaceIdentityDataSource
) : CollaborationSpaceIdentityRepository {
    override fun selection(
        spaceId: UUID,
        accountId: UUID,
        serverName: String,
    ): Result<CollaborationSpaceIdentityInput?> {
        if (!validCollaborationSpaceServer(serverName))
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_identity_query")
            )
        return safeDatabaseCall {
            source.selection(spaceId, accountId, serverName)?.let { row ->
                require(row.spaceId == spaceId && row.accountId == accountId)
                require(row.selected == null || row.preferred == null)
                CollaborationSpaceIdentityInput(
                    spaceId,
                    accountId,
                    row.selected?.let { identityFromRow(it, spaceId, accountId) },
                    row.preferred?.let { legacyDeviceFromRow(it, spaceId, accountId) },
                )
            }
        }
    }

    override fun create(identity: CollaborationSpaceIdentity): Result<Unit> = safeDatabaseCall {
        val row =
            CollaborationSpaceIdentitiesRecord().apply {
                spaceId = identity.spaceId
                accountId = identity.accountId
                matrixServerName = identity.serverName
                matrixUserId = identity.matrixUserId
                origin = identity.origin.name
                legacyCompanyId = identity.legacy?.companyId
                legacyDeviceId = identity.legacy?.deviceId
                createdAt = identity.createdAt.atOffset(ZoneOffset.UTC)
                version = identity.version
            }
        // Creation and stored reads obey the same correlated identity mapping.
        identityFromRow(row, identity.spaceId, identity.accountId)
        source.insert(row)
    }

    private fun identityFromRow(
        row: CollaborationSpaceIdentitiesRecord,
        spaceId: UUID,
        accountId: UUID,
    ): CollaborationSpaceIdentity {
        require(row.spaceId == spaceId && row.accountId == accountId && row.version == 0L)
        val server = requireNotNull(row.matrixServerName)
        require(validCollaborationSpaceServer(server))
        val origin = CollaborationSpaceIdentityOrigin.valueOf(requireNotNull(row.origin))
        val legacy =
            when (origin) {
                CollaborationSpaceIdentityOrigin.LEGACY ->
                    CollaborationLegacyDevicePosition(
                        requireNotNull(row.legacyCompanyId),
                        requireNotNull(row.legacyDeviceId),
                    )
                CollaborationSpaceIdentityOrigin.SPACE -> {
                    require(row.legacyCompanyId == null && row.legacyDeviceId == null)
                    null
                }
            }
        val prefix =
            legacy?.let { collaborationMatrixLocalpart(it.companyId, accountId) }
                ?: collaborationSpaceMatrixLocalpart(spaceId, accountId)
        require(row.matrixUserId == "@$prefix:$server")
        return CollaborationSpaceIdentity(
            spaceId,
            accountId,
            server,
            requireNotNull(row.matrixUserId),
            origin,
            legacy,
            requireNotNull(row.createdAt).toInstant(),
        )
    }
}
