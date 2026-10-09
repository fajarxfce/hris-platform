package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.util.UUID

class GetEmployeeImportAttempts(
    private val imports: EmployeeImportRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<EmployeeImportAttempt>> {
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val batchGuard = imports.lock(company, id, shared = true)
            if (batchGuard is Result.Failed) return@run batchGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities.access(actor.accountId, company).flatMap {
                    validateEmployeeImportActor(actor, it)
                }
            if (authorized is Result.Failed) return@run authorized
            val found = imports.find(company, id)
            if (found is Result.Failed) return@run found
            if ((found as Result.Success).value == null)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "employee_import_not_found")
                )
            imports.attempts(company, id, after, limit)
        }
    }
}
