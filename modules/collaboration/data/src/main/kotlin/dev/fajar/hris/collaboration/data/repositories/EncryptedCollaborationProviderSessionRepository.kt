package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.crypto.collaborationProviderPurpose
import dev.fajar.hris.collaboration.data.datasources.CollaborationProviderSessionDataSource
import dev.fajar.hris.collaboration.domain.entities.CollaborationProviderSession
import dev.fajar.hris.collaboration.domain.repositories.CollaborationProviderSessionRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationProviderSessionsRecord
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class EncryptedCollaborationProviderSessionRepository(
    private val store: CollaborationProviderSessionDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationProviderSessionRepository {
    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationProviderSession?> = safeIdentityCall {
        store.find(companyId, accountId, deviceId)?.let { row ->
            val encrypted = row.tokenEncrypted ?: return@let null
            CollaborationProviderSession(
                row.companyId,
                row.accountId,
                row.deviceId,
                row.matrixUserId,
                row.matrixDeviceId,
                row.generation,
                crypto.decrypt(
                    collaborationProviderPurpose(
                        row.companyId,
                        row.accountId,
                        row.deviceId,
                        row.generation,
                        row.tokenExpiresAt?.toInstant(),
                    ),
                    encrypted,
                ),
                row.createdAt.toInstant(),
                row.updatedAt.toInstant(),
                row.tokenExpiresAt?.toInstant(),
            )
        }
    }

    override fun create(session: CollaborationProviderSession): Result<Unit> = safeIdentityCall {
        store.insert(encrypted(session))
    }

    override fun rotate(
        session: CollaborationProviderSession,
        expectedGeneration: Long,
    ): Result<Unit> =
        safeIdentityCall { store.replace(encrypted(session), expectedGeneration) }
            .flatMap(::changed)

    override fun retire(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedGeneration: Long,
        at: Instant,
    ): Result<Unit> =
        safeIdentityCall {
                store.clear(
                    companyId,
                    accountId,
                    deviceId,
                    expectedGeneration,
                    at.atOffset(ZoneOffset.UTC),
                )
            }
            .flatMap(::changed)

    private fun encrypted(
        session: CollaborationProviderSession
    ): CollaborationProviderSessionsRecord {
        require(
            session.accessToken.length in 1..2048 && session.accessToken.all { it.code in 33..126 }
        )
        val expiry = requireNotNull(session.expiresAt).truncatedTo(ChronoUnit.MICROS)
        require(expiry > session.updatedAt && expiry <= session.updatedAt.plusSeconds(600))
        return CollaborationProviderSessionsRecord().apply {
            companyId = session.companyId
            accountId = session.accountId
            deviceId = session.deviceId
            matrixUserId = session.matrixUserId
            matrixDeviceId = session.matrixDeviceId
            generation = session.generation
            tokenExpiresAt = expiry.atOffset(ZoneOffset.UTC)
            tokenEncrypted =
                crypto.encrypt(
                    collaborationProviderPurpose(
                        companyId,
                        accountId,
                        deviceId,
                        generation,
                        expiry,
                    ),
                    session.accessToken,
                )
            createdAt = session.createdAt.atOffset(ZoneOffset.UTC)
            updatedAt = session.updatedAt.atOffset(ZoneOffset.UTC)
        }
    }

    private fun changed(value: Boolean): Result<Unit> =
        if (value) Result.Success(Unit)
        else
            Result.Failed(
                Failure(FailureKind.CONFLICT, "collaboration_provider_generation_changed")
            )
}
