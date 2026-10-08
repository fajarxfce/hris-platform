package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.util.UUID

class GetLifecycleCase(
    private val lifecycle: LifecycleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID): Result<LifecycleCase> {
        val access = actor.requirePermission("people.lifecycle.read")
        if (access is Result.Failed) return access
        return transactions.run(actor) {
            lifecycle.case(requireNotNull(actor.companyId), id).flatMap {
                if (it == null)
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found"))
                else Result.Success(it)
            }
        }
    }
}
