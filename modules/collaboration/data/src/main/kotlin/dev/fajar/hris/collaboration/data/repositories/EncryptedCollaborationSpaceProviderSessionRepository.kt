package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.crypto.collaborationSpaceProviderPurpose
import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceProviderSessionDataSource
import dev.fajar.hris.collaboration.data.mappers.spaceProviderRow
import dev.fajar.hris.collaboration.data.mappers.spaceProviderState
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceProviderSessionRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationSpaceProviderSessionsRecord
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class EncryptedCollaborationSpaceProviderSessionRepository(
    private val store: CollaborationSpaceProviderSessionDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationSpaceProviderSessionRepository {
    override fun metadata(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationSpaceProviderState?> = safeIdentityCall {
        store.find(spaceId, accountId, deviceId)?.let {
            spaceProviderState(it, spaceId, accountId, deviceId)
        }
    }

    override fun credential(
        expected: CollaborationSpaceProviderState
    ): Result<CollaborationSpaceProviderSession> =
        safeIdentityCall {
                if (expected.clearedAt != null) return@safeIdentityCall null
                val row =
                    store.find(expected.spaceId, expected.accountId, expected.deviceId)
                        ?: return@safeIdentityCall null
                val state =
                    spaceProviderState(row, expected.spaceId, expected.accountId, expected.deviceId)
                if (state != expected) return@safeIdentityCall null
                val token =
                    crypto.decrypt(
                        collaborationSpaceProviderPurpose(state),
                        requireNotNull(row.tokenEncrypted),
                    )
                requireToken(token)
                CollaborationSpaceProviderSession(state, token)
            }
            .flatMap { if (it == null) conflict() else Result.Success(it) }

    override fun create(session: CollaborationSpaceProviderSession): Result<Unit> =
        safeIdentityCall {
            require(
                session.state.generation == 0L && session.state.createdAt == session.state.updatedAt
            )
            store.insert(encrypted(session))
        }

    override fun rotate(
        session: CollaborationSpaceProviderSession,
        expectedGeneration: Long,
    ): Result<Unit> =
        safeIdentityCall {
                require(
                    expectedGeneration in 0..999998 &&
                        session.state.generation == expectedGeneration + 1
                )
                store.replace(encrypted(session), expectedGeneration)
            }
            .flatMap { if (it) Result.Success(Unit) else conflict() }

    override fun retire(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedGeneration: Long,
        at: Instant,
    ): Result<Unit> =
        safeIdentityCall {
                require(expectedGeneration in 0..999999)
                store.clear(
                    spaceId,
                    accountId,
                    deviceId,
                    expectedGeneration,
                    at.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC),
                )
            }
            .flatMap { if (it) Result.Success(Unit) else conflict() }

    private fun encrypted(
        session: CollaborationSpaceProviderSession
    ): CollaborationSpaceProviderSessionsRecord {
        require(session.state.clearedAt == null)
        requireToken(session.accessToken)
        val row = spaceProviderRow(session.state).apply { tokenEncrypted = "pending-encryption" }
        val state =
            spaceProviderState(
                row,
                session.state.spaceId,
                session.state.accountId,
                session.state.deviceId,
            )
        row.tokenEncrypted =
            crypto.encrypt(collaborationSpaceProviderPurpose(state), session.accessToken)
        require(requireNotNull(row.tokenEncrypted).length in 1..4096)
        return row
    }

    private fun requireToken(token: String) {
        require(token.length in 1..2048 && token.all { it.code in 33..126 })
    }

    private fun <T> conflict(): Result<T> =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_provider_generation_changed"))
}
