package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Queue opaque hints after publication commits; delivery owns current recipient admission. */
class FanOutCollaborationReadiness(
    private val wakes: CollaborationReadinessWakeRepository,
    private val bindings: CollaborationPushBindingRepository,
    private val pushes: CollaborationPushQueueRepository,
    private val transactions: TransactionRunner,
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun claim(ownerId: UUID, limit: Int): Result<List<CollaborationReadinessLease>> {
        if (!enabled()) return Result.Success(emptyList())
        if (limit !in 1..4) return invalid()
        return transactions.run(actor(UUID(0, 0), null)) {
            wakes.claim(ownerId, limit).flatMap { claimed ->
                if (
                    claimed.size > limit ||
                        claimed.map { it.eventId }.distinct().size != claimed.size ||
                        claimed.any { it.ownerId != ownerId || it.expiresAt <= clock.instant() }
                )
                    lost()
                else Result.Success(claimed)
            }
        }
    }

    /** One finite page; a failed transaction leaves the original lease/checkpoint recoverable. */
    fun fanOut(lease: CollaborationReadinessLease): Result<Unit> {
        if (!enabled()) return unavailable()
        val deadline = minOf(clock.instant().plusSeconds(20), lease.expiresAt)
        val publisher = actor(lease.publisherAccountId, lease.companyId)
        val acquired =
            step(deadline) {
                transactions.run(publisher) {
                    wakes.find(lease, false).flatMap { checkpoint ->
                        if (checkpoint == null || checkpoint.lease != lease) lost()
                        else
                            wakes.targets(checkpoint, 8).flatMap { targets ->
                                val positions = targets.map { it.position }
                                if (
                                    targets.size > 8 ||
                                        positions != positions.distinct().sorted() ||
                                        positions.any {
                                            it.accountId == lease.publisherAccountId ||
                                                (checkpoint.after != null && it <= checkpoint.after)
                                        }
                                )
                                    lost()
                                else Result.Success(checkpoint to targets)
                            }
                    }
                }
            }
        if (acquired is Result.Failed) return acquired
        val (initial, targets) = (acquired as Result.Success).value
        var checkpoint = initial
        for (target in targets) {
            val expected = checkpoint
            val advanced =
                step(deadline) {
                    transactions.run(actor(target.position.accountId, lease.companyId)) {
                        wakes.find(lease, true).flatMap { current ->
                            if (current != expected) return@flatMap lost()
                            bindings
                                .find(
                                    lease.companyId,
                                    target.position.accountId,
                                    target.position.deviceId,
                                )
                                .flatMap { binding ->
                                    val append =
                                        if (
                                            binding != null &&
                                                binding.companyId == lease.companyId &&
                                                binding.accountId == target.position.accountId &&
                                                binding.deviceId == target.position.deviceId &&
                                                binding.nativeSessionId == target.nativeSessionId &&
                                                binding.version == 1L &&
                                                binding.publishedAt != null &&
                                                binding.publishedAt <= expected.publicationCutoff
                                        )
                                            pushes.append(binding, null, 300).map { Unit }
                                        else Result.Success(Unit)
                                    append.flatMap {
                                        wakes.advance(expected, target.position).flatMap { changed
                                            ->
                                            if (!changed) lost()
                                            else step(deadline) { Result.Success(Unit) }
                                        }
                                    }
                                }
                        }
                    }
                }
            if (advanced is Result.Failed) return advanced
            checkpoint = expected.copy(after = target.position)
        }
        val expected = checkpoint
        return step(deadline) {
            transactions.run(publisher) {
                wakes.find(lease, true).flatMap { current ->
                    if (current != expected) lost()
                    else
                        wakes.release(expected, targets.size < 8, clock.instant()).flatMap {
                            if (!it) lost() else step(deadline) { Result.Success(Unit) }
                        }
                }
            }
        }
    }

    private fun enabled() = service.enabled && service.pushEnabled && provider.enabled

    private fun actor(accountId: UUID, companyId: UUID?) =
        Actor(accountId, companyId, emptySet(), clock.instant(), UUID.randomUUID())

    private fun <T> step(deadline: Instant, operation: () -> Result<T>): Result<T> {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (clock.instant() >= deadline) return lost()
        val result = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        return if (clock.instant() >= deadline) lost() else result
    }

    private fun lost() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_readiness_lease_lost"))

    private fun invalid() =
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_readiness_batch"))

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_readiness_unavailable"))
}
