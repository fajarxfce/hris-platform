package dev.fajar.hris.storage.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.domain.entities.ObjectCleanupEntry
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import java.util.UUID

class ListObjectCleanup(
    private val queue: ObjectCleanupRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<ObjectCleanupEntry>> {
        val access = actor.requirePermission("jobs.read")
        if (access is Result.Failed) return access
        if (actor.companyId == null || limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_cleanup_query"))
        return transactions.run(actor) { queue.list(requireNotNull(actor.companyId), after, limit) }
    }
}
