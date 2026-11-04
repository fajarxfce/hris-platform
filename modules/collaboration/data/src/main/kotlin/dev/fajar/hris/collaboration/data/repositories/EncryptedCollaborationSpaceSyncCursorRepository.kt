package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpaceHistoryIdentity
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceSyncCursorRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource

class EncryptedCollaborationSpaceSyncCursorRepository(private val crypto: IdentityTokenDataSource) :
    CollaborationSpaceSyncCursorRepository {
    override fun seal(
        owner: CollaborationSpaceHistoryOwner,
        position: CollaborationSyncPosition,
    ): Result<String> = safeCollaborationSyncCursorCall {
        sealCollaborationSyncPosition(crypto, purpose(owner), position)
    }

    override fun open(
        owner: CollaborationSpaceHistoryOwner,
        cursor: String,
    ): Result<CollaborationSyncPosition?> =
        safeCollaborationSyncCursorCall { purpose(owner) }
            .flatMap { purpose ->
                val current = safeCollaborationSyncCursorCall {
                    openCollaborationSyncPosition(crypto, purpose, cursor)
                }
                val original = owner.legacy
                if (
                    current is Result.Failed &&
                        current.failure ==
                            Failure(FailureKind.VALIDATION, "invalid_collaboration_sync_cursor") &&
                        original != null
                ) {
                    val originalOwner =
                        CollaborationSyncOwner(
                            original.companyId,
                            owner.accountId,
                            original.deviceId,
                            owner.serverName,
                        )
                    // Authenticate but never reuse the old scope, filter or since after ownership
                    // changes.
                    safeCollaborationSyncCursorCall {
                        openCollaborationSyncPosition(
                            crypto,
                            collaborationCompanySyncPurpose(crypto, originalOwner),
                            cursor,
                        )
                        null
                    }
                } else current
            }

    private fun purpose(owner: CollaborationSpaceHistoryOwner): String {
        require(validCollaborationSpaceHistoryIdentity(owner))
        return "collaboration.space.sync.v1:" +
            crypto.hash(
                "${owner.spaceId}\n${owner.accountId}\n${owner.deviceId}\n${owner.serverName}\n${owner.matrixUserId}\n${owner.matrixDeviceId}"
            )
    }
}
