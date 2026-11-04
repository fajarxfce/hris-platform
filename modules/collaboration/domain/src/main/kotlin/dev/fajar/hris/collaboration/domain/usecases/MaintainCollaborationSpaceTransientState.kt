package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceGatewaySessionRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRequestRepository
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.util.UUID

/** One atomic, bounded worker pass; retained identities and current credentials are untouched. */
class MaintainCollaborationSpaceTransientState(
    private val gateway: CollaborationSpaceGatewaySessionRepository,
    private val requests: CollaborationSpaceRequestRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(limit: Int = 100): Result<Int> {
        if (limit !in 1..100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_cleanup_limit"))
        val now = clock.instant()
        return transactions.run(Actor(UUID(0, 0), null, emptySet(), now, UUID.randomUUID())) {
            gateway.clearExpiredReplays(now, limit).flatMap { replays ->
                requests.clearExpired(now, limit).map { leases -> replays + leases }
            }
        }
    }
}
