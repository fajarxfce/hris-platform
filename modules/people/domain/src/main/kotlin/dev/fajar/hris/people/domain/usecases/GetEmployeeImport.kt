package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.util.UUID

class GetEmployeeImport(
    private val imports: EmployeeImportRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID): Result<EmployeeImportSummary> {
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
        return transactions.run(actor) {
            val found = imports.find(requireNotNull(actor.companyId), id)
            if (found is Result.Failed) return@run found
            val batch =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_import_not_found")
                    )
            imports.counts(requireNotNull(actor.companyId), id).map {
                EmployeeImportSummary(batch, it)
            }
        }
    }
}
