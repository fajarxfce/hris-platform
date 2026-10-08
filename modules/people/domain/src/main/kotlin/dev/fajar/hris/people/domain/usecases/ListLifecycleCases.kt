package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.util.UUID

class ListLifecycleCases(
    private val lifecycle: LifecycleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID?,
        status: LifecycleStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<LifecycleCase>> {
        val access = actor.requirePermission("people.lifecycle.read")
        if (access is Result.Failed) return access
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            lifecycle.cases(requireNotNull(actor.companyId), employeeId, status, after, limit)
        }
    }
}
