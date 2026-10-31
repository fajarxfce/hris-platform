package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.repositories.CollaborationGatewaySessionRepository
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.util.UUID

/** Worker capability clears one bounded batch of expired response caches, never live tokens. */
class MaintainCollaborationGatewayReplays(
    private val gateway: CollaborationGatewaySessionRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(limit: Int = 100): Result<Int> {
        if (limit !in 1..100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_cleanup_limit"))
        val now = clock.instant()
        return transactions.run(Actor(UUID(0, 0), null, emptySet(), now, UUID.randomUUID())) {
            gateway.clearExpiredReplays(now, limit)
        }
    }
}
