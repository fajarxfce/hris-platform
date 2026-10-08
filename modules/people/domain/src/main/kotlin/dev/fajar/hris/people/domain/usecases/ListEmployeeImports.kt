package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.util.UUID

class ListEmployeeImports(
    private val imports: EmployeeImportRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<EmployeeImport>> {
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            imports.list(requireNotNull(actor.companyId), after, limit)
        }
    }
}
