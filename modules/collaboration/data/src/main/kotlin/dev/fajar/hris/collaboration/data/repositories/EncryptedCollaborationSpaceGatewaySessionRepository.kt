package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.crypto.collaborationSpaceGatewayPurpose
import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceGatewaySessionDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceGatewaySessionRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationSpaceGatewaySessionsRecord
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class EncryptedCollaborationSpaceGatewaySessionRepository(
    private val source: CollaborationSpaceGatewaySessionDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationSpaceGatewaySessionRepository {
    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): Result<CollaborationSpaceGatewaySession?> = safeIdentityCall {
        source.find(spaceId, accountId, deviceId, lock)?.let {
            scoped(it, spaceId, accountId, deviceId)
        }
    }

    override fun findAccess(token: String): Result<CollaborationSpaceGatewaySession?> =
        safeIdentityCall {
            require(validCollaborationSpaceGatewayAccess(token))
            val hash = crypto.hash(token)
            source.byAccess(hash)?.let {
                require(it.accessHash == hash)
                it.toSpaceGatewaySession()
            }
        }

    override fun findRefresh(token: String): Result<CollaborationSpaceGatewayRefresh?> =
        safeIdentityCall {
            require(validCollaborationSpaceGatewayRefresh(token))
            val hash = crypto.hash(token)
            source.byRefresh(hash)?.let {
                require(it.refreshHash == hash || it.previousRefreshHash == hash)
                CollaborationSpaceGatewayRefresh(
                    it.toSpaceGatewaySession(),
                    it.refreshHash != hash,
                    it.replayUntil?.toInstant(),
                )
            }
        }

    override fun create(
        session: CollaborationSpaceGatewaySession,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<CollaborationSpaceGatewayTokens> = safeIdentityCall {
        require(
            session.version == 0L &&
                session.createdAt == session.rotatedAt &&
                session.revokedAt == null
        )
        val record =
            session.toSpaceGatewayRecord().apply {
                replayKind = "BOOTSTRAP"
                bootstrapOperationId = operationId
            }
        val tokens = credentials(record, replayUntil)
        source.insert(record)
        verifyStored(record)
        tokens
    }

    override fun rotateRefresh(
        session: CollaborationSpaceGatewaySession,
        token: String,
        replayUntil: Instant,
    ): Result<CollaborationSpaceGatewayTokens> {
        if (!validCollaborationSpaceGatewayRefresh(token)) return changed()
        return rotate(session, token, null, replayUntil)
    }

    override fun rotateBootstrap(
        session: CollaborationSpaceGatewaySession,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<CollaborationSpaceGatewayTokens> = rotate(session, null, operationId, replayUntil)

    private fun rotate(
        session: CollaborationSpaceGatewaySession,
        token: String?,
        operationId: UUID?,
        replayUntil: Instant,
    ): Result<CollaborationSpaceGatewayTokens> =
        safeIdentityCall {
                require(session.version in 1..999999 && session.revokedAt == null)
                val old = source.find(session.spaceId, session.accountId, session.deviceId, false)
                if (
                    old == null ||
                        old.id != session.id ||
                        old.version != session.version - 1 ||
                        old.revokedAt != null
                )
                    return@safeIdentityCall null
                val current = scoped(old, session.spaceId, session.accountId, session.deviceId)
                require(
                    current.nativeSessionId == session.nativeSessionId &&
                        current.platform == session.platform &&
                        current.createdAt == session.createdAt &&
                        current.expiresAt == session.expiresAt
                )
                require(session.revokedAt == null && session.rotatedAt >= current.rotatedAt)
                if (token != null) require(session.clientBuild == current.clientBuild)
                val oldHash = requireNotNull(old.refreshHash)
                if (token != null && crypto.hash(token) != oldHash) return@safeIdentityCall null
                val record =
                    session.toSpaceGatewayRecord().apply {
                        replayKind = if (token == null) "BOOTSTRAP" else "REFRESH"
                        bootstrapOperationId = operationId
                        previousRefreshHash = if (token == null) null else oldHash
                    }
                val tokens = credentials(record, replayUntil)
                if (source.replace(record, session.version - 1, oldHash)) {
                    verifyStored(record)
                    tokens
                } else null
            }
            .flatMap { if (it == null) changed() else Result.Success(it) }

    override fun replayBootstrap(
        session: CollaborationSpaceGatewaySession,
        operationId: UUID,
    ): Result<CollaborationSpaceGatewayTokens?> = safeIdentityCall {
        source
            .find(session.spaceId, session.accountId, session.deviceId, false)
            ?.takeIf {
                it.id == session.id &&
                    it.version == session.version &&
                    it.replayKind == "BOOTSTRAP" &&
                    it.bootstrapOperationId == operationId
            }
            ?.let {
                if (scoped(it, session.spaceId, session.accountId, session.deviceId) != session)
                    null
                else replay(it)
            }
    }

    override fun replayRefresh(
        session: CollaborationSpaceGatewaySession,
        token: String,
    ): Result<CollaborationSpaceGatewayTokens?> = safeIdentityCall {
        require(validCollaborationSpaceGatewayRefresh(token))
        val hash = crypto.hash(token)
        source
            .find(session.spaceId, session.accountId, session.deviceId, false)
            ?.takeIf {
                it.id == session.id &&
                    it.version == session.version &&
                    it.replayKind == "REFRESH" &&
                    it.previousRefreshHash == hash
            }
            ?.let {
                if (scoped(it, session.spaceId, session.accountId, session.deviceId) != session)
                    null
                else replay(it)
            }
    }

    override fun retire(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: Instant,
    ): Result<Unit> =
        safeIdentityCall {
                require(expectedVersion in 0..999999)
                val old =
                    source.find(spaceId, accountId, deviceId, false)
                        ?: return@safeIdentityCall false
                val current = scoped(old, spaceId, accountId, deviceId)
                if (current.revokedAt != null || current.version != expectedVersion)
                    return@safeIdentityCall false
                val whenRetired = at.truncatedTo(ChronoUnit.MICROS)
                require(whenRetired >= current.rotatedAt)
                if (
                    !source.retire(
                        spaceId,
                        accountId,
                        deviceId,
                        expectedVersion,
                        whenRetired.atOffset(ZoneOffset.UTC),
                    )
                )
                    return@safeIdentityCall false
                val retained = requireNotNull(source.find(spaceId, accountId, deviceId, false))
                require(
                    scoped(retained, spaceId, accountId, deviceId) ==
                        current.copy(version = expectedVersion + 1, revokedAt = whenRetired)
                )
                true
            }
            .flatMap { if (it) Result.Success(Unit) else changed() }

    override fun clearExpiredReplays(at: Instant, limit: Int): Result<Int> = safeIdentityCall {
        require(limit in 1..100)
        source.clearExpiredReplays(at.atOffset(ZoneOffset.UTC), limit).also {
            require(it in 0..limit)
        }
    }

    private fun scoped(
        row: CollaborationSpaceGatewaySessionsRecord,
        space: UUID,
        account: UUID,
        device: UUID,
    ): CollaborationSpaceGatewaySession {
        val result = row.toSpaceGatewaySession()
        require(result.spaceId == space && result.accountId == account && result.deviceId == device)
        return result
    }

    private fun verifyStored(expected: CollaborationSpaceGatewaySessionsRecord) {
        val retained =
            requireNotNull(
                source.find(expected.spaceId, expected.accountId, expected.deviceId, false)
            )
        require(
            scoped(retained, expected.spaceId, expected.accountId, expected.deviceId) ==
                expected.toSpaceGatewaySession()
        )
        require(
            retained.accessHash == expected.accessHash &&
                retained.refreshHash == expected.refreshHash &&
                retained.replayKind == expected.replayKind &&
                retained.bootstrapOperationId == expected.bootstrapOperationId &&
                retained.previousRefreshHash == expected.previousRefreshHash &&
                retained.replayUntil?.toInstant() == expected.replayUntil?.toInstant() &&
                retained.replayEncrypted == expected.replayEncrypted
        )
    }

    private fun credentials(
        record: CollaborationSpaceGatewaySessionsRecord,
        until: Instant,
    ): CollaborationSpaceGatewayTokens {
        val access = COLLABORATION_SPACE_GATEWAY_ACCESS_PREFIX + crypto.generate()
        val refresh = COLLABORATION_SPACE_GATEWAY_REFRESH_PREFIX + crypto.generate()
        require(
            validCollaborationSpaceGatewayAccess(access) &&
                validCollaborationSpaceGatewayRefresh(refresh)
        )
        record.accessHash = crypto.hash(access)
        record.refreshHash = crypto.hash(refresh)
        record.replayUntil = until.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        record.replayEncrypted =
            crypto.encrypt(collaborationSpaceGatewayPurpose(record), "$access\n$refresh")
        record.toSpaceGatewaySession()
        return tokens(record, access, refresh)
    }

    private fun replay(
        record: CollaborationSpaceGatewaySessionsRecord
    ): CollaborationSpaceGatewayTokens? {
        record.toSpaceGatewaySession()
        val encrypted = record.replayEncrypted ?: return null
        val pair = crypto.decrypt(collaborationSpaceGatewayPurpose(record), encrypted).split('\n')
        require(
            pair.size == 2 &&
                validCollaborationSpaceGatewayAccess(pair[0]) &&
                validCollaborationSpaceGatewayRefresh(pair[1])
        )
        require(
            crypto.hash(pair[0]) == record.accessHash && crypto.hash(pair[1]) == record.refreshHash
        )
        return tokens(record, pair[0], pair[1])
    }

    private fun tokens(
        record: CollaborationSpaceGatewaySessionsRecord,
        access: String,
        refresh: String,
    ) =
        CollaborationSpaceGatewayTokens(
            record.id,
            record.version,
            access,
            refresh,
            record.accessExpiresAt.toInstant(),
            record.expiresAt.toInstant(),
            requireNotNull(record.replayUntil).toInstant(),
        )

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_gateway_changed"))
}
