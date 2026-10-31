package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Callers own transactions and authorization; opaque-token lookups only identify the next lock. */
interface CollaborationGatewaySessionRepository {
    fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean = false,
    ): Result<CollaborationGatewaySession?>

    /** Must run in its own auth-lookup transaction, followed by current scoped HRIS admission. */
    fun findAccess(token: String): Result<CollaborationGatewaySession?>

    fun findRefresh(token: String): Result<CollaborationGatewayRefresh?>

    fun create(
        session: CollaborationGatewaySession,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<CollaborationGatewayTokens>

    fun rotateRefresh(
        session: CollaborationGatewaySession,
        token: String,
        replayUntil: Instant,
    ): Result<CollaborationGatewayTokens>

    fun rotateBootstrap(
        session: CollaborationGatewaySession,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<CollaborationGatewayTokens>

    fun replayBootstrap(
        session: CollaborationGatewaySession,
        operationId: UUID,
    ): Result<CollaborationGatewayTokens?>

    fun replayRefresh(
        session: CollaborationGatewaySession,
        token: String,
    ): Result<CollaborationGatewayTokens?>

    fun retire(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: Instant,
    ): Result<Unit>

    /** Only the expired encrypted response cache; retained identity and token hashes survive. */
    fun clearExpiredReplays(at: Instant, limit: Int): Result<Int>
}
