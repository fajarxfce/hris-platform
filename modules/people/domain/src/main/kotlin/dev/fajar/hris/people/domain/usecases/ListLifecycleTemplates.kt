package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*

class ListLifecycleTemplates(
    private val lifecycle: LifecycleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, after: String?, limit: Int): Result<Page<LifecycleTemplate>> {
        val access = actor.requirePermission("people.lifecycle.read")
        if (access is Result.Failed) return access
        if (
            limit !in 1..200 || (after != null && !after.matches(Regex("[A-Z0-9][A-Z0-9_-]{1,31}")))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            lifecycle.templates(requireNotNull(actor.companyId), after, limit)
        }
    }
}
