package dev.fajar.hris.storage.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.domain.entities.ObjectCleanupStatus
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import java.time.Clock
import java.util.UUID

class RetryObjectCleanup(
    private val queue: ObjectCleanupRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        key: UUID,
        id: UUID,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("jobs.retry")
        if (access is Result.Failed) return access
        if (actor.companyId == null || version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_cleanup_retry"))
        val company = requireNotNull(actor.companyId)
        val operation =
            OperationKey(
                "storage.cleanup_retry",
                key,
                listOf(id.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, operation)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = queue.find(company, id)
            if (found is Result.Failed) return@run found
            val item =
                (found as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "cleanup_not_found"))
            if (item.version != version || item.status != ObjectCleanupStatus.FAILED)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "cleanup_not_retryable"))
            queue.retry(company, id, version, clock.instant()).flatMap { receipt ->
                if (receipt == null) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                else
                    operations
                        .record(actor, operation, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "stored_object",
                                    id,
                                    "storage.cleanup_retry_requested",
                                    reason = reason,
                                ),
                            )
                        }
                        .map { receipt }
            }
        }
    }
}
