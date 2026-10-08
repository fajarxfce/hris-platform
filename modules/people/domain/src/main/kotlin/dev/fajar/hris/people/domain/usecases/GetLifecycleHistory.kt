package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.util.UUID

class GetLifecycleHistory(
    private val lifecycle: LifecycleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID, after: Long?, limit: Int): Result<Page<LifecycleEvent>> {
        val access = actor.requirePermission("people.lifecycle.read")
        if (access is Result.Failed) return access
        if (limit !in 1..200 || (after ?: 0) < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val found = lifecycle.case(requireNotNull(actor.companyId), id)
            if (found is Result.Failed) return@run found
            if ((found as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found"))
            lifecycle.history(requireNotNull(actor.companyId), id, after, limit)
        }
    }
}
