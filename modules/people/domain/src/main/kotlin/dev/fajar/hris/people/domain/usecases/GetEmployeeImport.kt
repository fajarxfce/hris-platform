package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetEmployeeImport(
    private val imports: EmployeeImportRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val jobs: JobRepository,
) {
    fun execute(actor: Actor, id: UUID): Result<EmployeeImportSummary> {
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
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
                    validateEmployeeImportSessionActor(actor, it, clock.instant(), security)
                }
            if (authorized is Result.Failed) return@run authorized
            val found = imports.find(company, id)
            if (found is Result.Failed) return@run found
            val batch =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_import_not_found")
                    )
            val counts = imports.counts(company, id)
            if (counts is Result.Failed) return@run counts
            // Lease owners lock the job before the import; this observation must not lock it.
            val foundJob = jobs.find(company, batch.jobId)
            if (foundJob is Result.Failed) return@run foundJob
            val job =
                (foundJob as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "employee_import_job_missing")
                    )
            if (
                job.request.kind !in
                    setOf(JobKind.EMPLOYEE_IMPORT_PREVIEW, JobKind.EMPLOYEE_IMPORT_APPLY) ||
                    job.request.values["importId"] != id.toString()
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "employee_import_job_mismatch")
                )
            val observedCounts = (counts as Result.Success).value
            Result.Success(
                EmployeeImportSummary(
                    batch,
                    observedCounts,
                    job.status,
                    job.cancellationRequested,
                    availableEmployeeImportActions(
                        batch.status,
                        observedCounts,
                        job.status,
                        job.cancellationRequested,
                    ),
                )
            )
        }
    }
}
