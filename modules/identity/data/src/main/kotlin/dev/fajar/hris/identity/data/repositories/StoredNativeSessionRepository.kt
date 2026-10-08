package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.mappers.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.NativeSessionRepository
import dev.fajar.hris.schema.tables.records.ConsumedRefreshTokensRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class StoredNativeSessionRepository(
    private val store: NativeSessionDataSource,
    private val crypto: IdentityTokenDataSource,
) : NativeSessionRepository {
    override fun lockAccount(accountId: UUID): Result<Account?> = safeIdentityCall {
        store.lockAccount(accountId)?.toAccount()
    }

    override fun findAccess(token: String): Result<NativeSession?> = safeIdentityCall {
        store.byAccess(crypto.hash(token))?.toNativeSession()
    }

    override fun findRefresh(token: String): Result<NativeRefresh?> = safeIdentityCall {
        val hash = crypto.hash(token)
        val current = store.byRefresh(hash)
        if (current != null) NativeRefresh(current.toNativeSession(), false)
        else {
            val consumed = store.consumed(hash)
            val session = consumed?.sessionId?.let { store.get(it) }
            if (consumed == null || session == null) null
            else
                NativeRefresh(
                    session.toNativeSession(),
                    true,
                    consumed.operationId,
                    consumed.successorVersion,
                    consumed.replayUntil?.toInstant(),
                )
        }
    }

    override fun lock(sessionId: UUID, accountId: UUID): Result<NativeSession?> = safeIdentityCall {
        store.get(sessionId, accountId, true)?.toNativeSession()
    }

    override fun findExchange(accountId: UUID, operationId: UUID): Result<NativeSession?> =
        safeIdentityCall {
            store.exchange(accountId, operationId)?.toNativeSession()
        }

    override fun replayExchange(session: NativeSession): Result<NativeTokens?> = safeIdentityCall {
        store.get(session.id, session.accountId)?.exchangeEncrypted?.let {
            decodeNativeTokens(
                session,
                crypto.decrypt(nativeTokenPurpose(session, session.exchangeOperationId), it),
            )
        }
    }

    override fun replayRefresh(
        token: String,
        session: NativeSession,
        operationId: UUID,
    ): Result<NativeTokens?> = safeIdentityCall {
        store.consumed(crypto.hash(token))?.replayEncrypted?.let {
            decodeNativeTokens(
                session,
                crypto.decrypt(nativeTokenPurpose(session, operationId), it),
            )
        }
    }

    override fun list(accountId: UUID, at: Instant, limit: Int): Result<List<NativeSession>> =
        safeIdentityCall {
            store.list(accountId, at, limit).map { it.toNativeSession() }
        }

    override fun countIssued(accountId: UUID, at: Instant): Result<Long> = safeIdentityCall {
        store.countIssued(accountId, at)
    }

    override fun create(session: NativeSession, replayUntil: Instant): Result<NativeTokens> =
        safeIdentityCall {
            val access = crypto.generate()
            val refresh = crypto.generate()
            val record = session.toRecord()
            record.accessHash = crypto.hash(access)
            record.refreshHash = crypto.hash(refresh)
            record.exchangeEncrypted =
                crypto.encrypt(
                    nativeTokenPurpose(session, session.exchangeOperationId),
                    "$access\n$refresh",
                )
            record.exchangeReplayUntil = OffsetDateTime.ofInstant(replayUntil, ZoneOffset.UTC)
            store.insert(record)
            NativeTokens(session.id, access, refresh, session.accessExpiresAt, session.expiresAt)
        }

    override fun rotate(
        session: NativeSession,
        oldToken: String?,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<NativeTokens> = safeIdentityCall {
        val old = requireNotNull(store.get(session.id, session.accountId))
        val oldHash = oldToken?.let(crypto::hash) ?: old.refreshHash
        val access = crypto.generate()
        val refresh = crypto.generate()
        val encrypted =
            crypto.encrypt(nativeTokenPurpose(session, operationId), "$access\n$refresh")
        store.clearExpiredReplays(session.id, session.rotatedAt)
        store.consume(
            ConsumedRefreshTokensRecord().apply {
                tokenHash = oldHash
                sessionId = session.id
                expiresAt = OffsetDateTime.ofInstant(session.expiresAt, ZoneOffset.UTC)
                this.operationId = operationId
                successorVersion = session.version
                // MFA elevation returns credentials once and cannot be replayed as an ordinary
                // refresh.
                if (oldToken != null) {
                    replayEncrypted = encrypted
                    this.replayUntil = OffsetDateTime.ofInstant(replayUntil, ZoneOffset.UTC)
                }
            }
        )
        val record = session.toRecord()
        record.accessHash = crypto.hash(access)
        record.refreshHash = crypto.hash(refresh)
        record.exchangeEncrypted = null
        record.exchangeReplayUntil = null
        if (store.update(record, session.version - 1, oldHash) != 1)
            throw org.springframework.dao.OptimisticLockingFailureException(
                "native_session_version"
            )
        NativeTokens(session.id, access, refresh, session.accessExpiresAt, session.expiresAt)
    }

    override fun revoke(sessionId: UUID, accountId: UUID, at: Instant): Result<Boolean> =
        safeIdentityCall {
            store.revoke(sessionId, accountId, at) == 1
        }

    override fun purgeExpired(accountId: UUID, before: Instant, limit: Int): Result<Unit> =
        safeIdentityCall {
            store.purgeExpired(accountId, before, limit)
        }
}
