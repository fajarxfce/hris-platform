package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.util.UUID

class GetEmployeeImportRows(
    private val imports: EmployeeImportRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID, after: Int?, limit: Int): Result<Page<EmployeeImportRow>> {
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
        if (limit !in 1..200 || (after != null && after !in 0..5000))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val found = imports.find(requireNotNull(actor.companyId), id)
            if (found is Result.Failed) return@run found
            if ((found as Result.Success).value == null)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "employee_import_not_found")
                )
            imports.rows(requireNotNull(actor.companyId), id, after, limit)
        }
    }
}
