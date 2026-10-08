package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*

class ListAssignedLifecycleTasks(
    private val lifecycle: LifecycleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, after: String?, limit: Int): Result<Page<AssignedLifecycleTask>> {
        if (
            actor.companyId == null ||
                actor.permissions.none {
                    it == "people.lifecycle.perform" || it == "people.lifecycle.manage"
                }
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "lifecycle_access_required"))
        if (
            limit !in 1..200 ||
                (after != null &&
                    !after.matches(
                        Regex(
                            "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}:[a-z][a-z0-9_-]{0,47}"
                        )
                    ))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            lifecycle.assigned(requireNotNull(actor.companyId), actor.accountId, after, limit)
        }
    }
}
