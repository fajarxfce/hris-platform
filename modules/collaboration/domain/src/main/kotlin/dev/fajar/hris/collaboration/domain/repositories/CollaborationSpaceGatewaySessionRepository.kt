package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Callers own transactions and authorization; opaque-token lookups only identify the next lock. */
interface CollaborationSpaceGatewaySessionRepository {
    fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean = false,
    ): Result<CollaborationSpaceGatewaySession?>

    /** Must run in its own auth-lookup transaction, followed by current scoped HRIS admission. */
    fun findAccess(token: String): Result<CollaborationSpaceGatewaySession?>

    fun findRefresh(token: String): Result<CollaborationSpaceGatewayRefresh?>

    fun create(
        session: CollaborationSpaceGatewaySession,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<CollaborationSpaceGatewayTokens>

    fun rotateRefresh(
        session: CollaborationSpaceGatewaySession,
        token: String,
        replayUntil: Instant,
    ): Result<CollaborationSpaceGatewayTokens>

    fun rotateBootstrap(
        session: CollaborationSpaceGatewaySession,
        operationId: UUID,
        replayUntil: Instant,
    ): Result<CollaborationSpaceGatewayTokens>

    fun replayBootstrap(
        session: CollaborationSpaceGatewaySession,
        operationId: UUID,
    ): Result<CollaborationSpaceGatewayTokens?>

    fun replayRefresh(
        session: CollaborationSpaceGatewaySession,
        token: String,
    ): Result<CollaborationSpaceGatewayTokens?>

    fun retire(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: Instant,
    ): Result<Unit>

    /** Only the expired encrypted response cache; retained identity and token hashes survive. */
    fun clearExpiredReplays(at: Instant, limit: Int): Result<Int>
}
