package dev.fajar.hris.storage.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.domain.repositories.*
import java.time.Clock
import java.util.UUID

class CollectObjectGarbage(
    private val queue: ObjectCleanupRepository,
    private val storage: ObjectStorageRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(owner: UUID, limit: Int = 2): Result<Int> {
        if (limit !in 1..4)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_cleanup_limit"))
        val expired = queue.exhausted(8, 4)
        if (expired is Result.Failed) return expired
        for (lease in (expired as Result.Success).value) {
            val request = lease.entry.request
            val actor =
                Actor(
                    request.createdBy,
                    request.companyId,
                    emptySet(),
                    clock.instant(),
                    UUID.randomUUID(),
                )
            val result =
                transactions.run(actor) {
                    queue.failExpired(lease).flatMap { changed ->
                        if (!changed) Result.Success(Unit)
                        else
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "stored_object",
                                    request.id,
                                    "storage.cleanup_exhausted",
                                    mapOf("resourceId" to request.resourceId.toString()),
                                ),
                            )
                    }
                }
            if (result is Result.Failed) return result
        }
        val claims = queue.claim(owner, limit, 120, 8)
        if (claims is Result.Failed) return claims
        var completed = 0
        for (lease in (claims as Result.Success).value) {
            val request = lease.entry.request
            // Physical deletion stays outside database transactions; repeating deletion is
            // harmless.
            val deleted = storage.delete(request.key)
            val actor =
                Actor(
                    request.createdBy,
                    request.companyId,
                    emptySet(),
                    clock.instant(),
                    UUID.randomUUID(),
                )
            val recorded =
                transactions.run(actor) {
                    when (deleted) {
                        is Result.Success ->
                            queue.complete(lease).flatMap { changed ->
                                if (!changed) Result.Success(false)
                                else
                                    journal
                                        .record(
                                            actor,
                                            ChangeRecord(
                                                "stored_object",
                                                request.id,
                                                "storage.object_deleted",
                                                mapOf("resourceId" to request.resourceId.toString()),
                                            ),
                                        )
                                        .map { true }
                            }
                        is Result.Failed -> {
                            val retryAt =
                                if (
                                    deleted.failure.kind == FailureKind.UNAVAILABLE &&
                                        lease.entry.attempts < 8
                                )
                                    clock
                                        .instant()
                                        .plusSeconds(minOf(300, 5L shl (lease.entry.attempts - 1)))
                                else null
                            queue.fail(lease, deleted.failure.code, retryAt).flatMap { changed ->
                                if (changed && retryAt == null)
                                    journal
                                        .record(
                                            actor,
                                            ChangeRecord(
                                                "stored_object",
                                                request.id,
                                                "storage.cleanup_failed",
                                                mapOf("code" to deleted.failure.code),
                                            ),
                                        )
                                        .map { false }
                                else Result.Success(false)
                            }
                        }
                    }
                }
            if (recorded is Result.Failed) return recorded
            if ((recorded as Result.Success).value) completed++
        }
        return Result.Success(completed)
    }
}
