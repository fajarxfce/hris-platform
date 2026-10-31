package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.crypto.collaborationGatewayPurpose
import dev.fajar.hris.collaboration.data.datasources.CollaborationGatewaySessionDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationGatewaySessionRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationGatewaySessionsRecord
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class EncryptedCollaborationGatewaySessionRepository(
    private val source: CollaborationGatewaySessionDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationGatewaySessionRepository {
    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): Result<CollaborationGatewaySession?> = safeIdentityCall {
        source.find(companyId, accountId, deviceId, lock)?.toGatewaySession()
    }

    override fun findAccess(token: String): Result<CollaborationGatewaySession?> =
        safeIdentityCall {
            require(validCollaborationGatewayAccess(token))
            source.byAccess(crypto.hash(token))?.toGatewaySession()
        }

    override fun findRefresh(token: String): Result<CollaborationGatewayRefresh?> =
        safeIdentityCall {
            require(validCollaborationGatewayRefresh(token))
            val hash = crypto.hash(token)
            source.byRefresh(hash)?.let {
                CollaborationGatewayRefresh(
                    it.toGatewaySession(),
                    it.refreshHash != hash,
                    it.replayUntil?.toInstant(),
                )
            }
        }

    override fun create(
        session: CollaborationGatewaySession,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<CollaborationGatewayTokens> = safeIdentityCall {
        val record =
            session.toGatewayRecord().apply {
                replayKind = "BOOTSTRAP"
                bootstrapOperationId = operationId
            }
        val tokens = credentials(record, replayUntil)
        source.insert(record)
        tokens
    }

    override fun rotateRefresh(
        session: CollaborationGatewaySession,
        token: String,
        replayUntil: Instant,
    ): Result<CollaborationGatewayTokens> {
        if (!validCollaborationGatewayRefresh(token)) return changed()
        return rotate(session, token, null, replayUntil)
    }

    override fun rotateBootstrap(
        session: CollaborationGatewaySession,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<CollaborationGatewayTokens> = rotate(session, null, operationId, replayUntil)

    private fun rotate(
        session: CollaborationGatewaySession,
        token: String?,
        operationId: UUID?,
        replayUntil: Instant,
    ): Result<CollaborationGatewayTokens> =
        safeIdentityCall {
                val old = source.find(session.companyId, session.accountId, session.deviceId, false)
                if (
                    old == null ||
                        old.id != session.id ||
                        old.version != session.version - 1 ||
                        old.revokedAt != null
                )
                    return@safeIdentityCall null
                val oldHash = requireNotNull(old.refreshHash)
                if (token != null && crypto.hash(token) != oldHash) return@safeIdentityCall null
                val record =
                    session.toGatewayRecord().apply {
                        replayKind = if (token == null) "BOOTSTRAP" else "REFRESH"
                        bootstrapOperationId = operationId
                        previousRefreshHash = if (token == null) null else oldHash
                    }
                val tokens = credentials(record, replayUntil)
                if (source.replace(record, session.version - 1, oldHash)) tokens else null
            }
            .flatMap { if (it == null) changed() else Result.Success(it) }

    override fun replayBootstrap(
        session: CollaborationGatewaySession,
        operationId: UUID,
    ): Result<CollaborationGatewayTokens?> = safeIdentityCall {
        source
            .find(session.companyId, session.accountId, session.deviceId, false)
            ?.takeIf {
                it.id == session.id &&
                    it.version == session.version &&
                    it.replayKind == "BOOTSTRAP" &&
                    it.bootstrapOperationId == operationId
            }
            ?.let(::replay)
    }

    override fun replayRefresh(
        session: CollaborationGatewaySession,
        token: String,
    ): Result<CollaborationGatewayTokens?> = safeIdentityCall {
        require(validCollaborationGatewayRefresh(token))
        val hash = crypto.hash(token)
        source
            .find(session.companyId, session.accountId, session.deviceId, false)
            ?.takeIf {
                it.id == session.id &&
                    it.version == session.version &&
                    it.replayKind == "REFRESH" &&
                    it.previousRefreshHash == hash
            }
            ?.let(::replay)
    }

    override fun retire(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: Instant,
    ): Result<Unit> =
        safeIdentityCall {
                source.retire(
                    companyId,
                    accountId,
                    deviceId,
                    expectedVersion,
                    at.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC),
                )
            }
            .flatMap { if (it) Result.Success(Unit) else changed() }

    override fun clearExpiredReplays(at: Instant, limit: Int): Result<Int> = safeIdentityCall {
        source.clearExpiredReplays(at.atOffset(ZoneOffset.UTC), limit)
    }

    private fun credentials(
        record: CollaborationGatewaySessionsRecord,
        until: Instant,
    ): CollaborationGatewayTokens {
        val access = COLLABORATION_GATEWAY_ACCESS_PREFIX + crypto.generate()
        val refresh = COLLABORATION_GATEWAY_REFRESH_PREFIX + crypto.generate()
        require(
            validCollaborationGatewayAccess(access) && validCollaborationGatewayRefresh(refresh)
        )
        record.accessHash = crypto.hash(access)
        record.refreshHash = crypto.hash(refresh)
        record.replayUntil = until.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        record.replayEncrypted =
            crypto.encrypt(collaborationGatewayPurpose(record), "$access\n$refresh")
        return tokens(record, access, refresh)
    }

    private fun replay(record: CollaborationGatewaySessionsRecord): CollaborationGatewayTokens? {
        val encrypted = record.replayEncrypted ?: return null
        val pair = crypto.decrypt(collaborationGatewayPurpose(record), encrypted).split('\n')
        require(
            pair.size == 2 &&
                validCollaborationGatewayAccess(pair[0]) &&
                validCollaborationGatewayRefresh(pair[1])
        )
        require(
            crypto.hash(pair[0]) == record.accessHash && crypto.hash(pair[1]) == record.refreshHash
        )
        return tokens(record, pair[0], pair[1])
    }

    private fun tokens(
        record: CollaborationGatewaySessionsRecord,
        access: String,
        refresh: String,
    ) =
        CollaborationGatewayTokens(
            record.id,
            record.version,
            access,
            refresh,
            record.accessExpiresAt.toInstant(),
            record.expiresAt.toInstant(),
        )

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_gateway_changed"))
}
