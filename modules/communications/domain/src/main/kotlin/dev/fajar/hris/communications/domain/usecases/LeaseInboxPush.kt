package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.communications.domain.repositories.InboxPushRepository
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.util.UUID

class LeaseInboxPush(
    private val dispatches: InboxPushRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val policy: InboxPushPolicy,
    private val clock: Clock,
) {
    fun execute(owner: UUID, limit: Int): Result<List<InboxPushLease>> {
        if (limit !in 1..8)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_push_capacity"))
        val now = clock.instant()
        val scope = Actor(UUID(0, 0), null, emptySet(), now, UUID.randomUUID())
        // Maintenance runs even when provider delivery is disabled.
        val expired =
            transactions.run(scope) {
                dispatches.purgeFinished(now.minus(policy.retention), 100).flatMap {
                    dispatches.exhausted(
                        now.minus(policy.deliveryLifetime),
                        policy.maximumAttempts,
                        100,
                    )
                }
            }
        if (expired is Result.Failed) return expired
        for (dispatch in (expired as Result.Success).value) {
            val actor = scope.copy(companyId = dispatch.companyId)
            val code =
                if (!dispatch.enqueuedAt.plus(policy.deliveryLifetime).isAfter(now))
                    "push_delivery_expired"
                else "push_attempts_exhausted"
            val failed =
                transactions.run(actor) {
                    dispatches.failUnleased(dispatch, code, now).flatMap { changed ->
                        if (!changed) Result.Success(Unit)
                        else
                            journal.record(
                                actor,
                                inboxPushFinishedRecord(dispatch, InboxPushState.FAILED, code),
                            )
                    }
                }
            if (failed is Result.Failed) return failed
        }
        if (!policy.enabled) return Result.Success(emptyList())
        return transactions.run(scope) {
            dispatches.claim(owner, limit, policy.leaseSeconds, policy.maximumAttempts)
        }
    }
}
