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

class ApplyEmployeeImport(
    private val imports: EmployeeImportRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        allowPartial: Boolean,
        reason: String,
    ): Result<MutationReceipt> {
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
        val assurance = requireEmployeeImportAssurance(actor, security, clock.instant())
        if (assurance is Result.Failed) return assurance
        if (version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employee_import"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "people.employee_import_apply",
                operationId,
                listOf(id.toString(), version.toString(), allowPartial.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val batchGuard = imports.lock(company, id)
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
            val currentAssurance = requireEmployeeImportAssurance(actor, security, clock.instant())
            if (currentAssurance is Result.Failed) return@run currentAssurance
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = imports.find(company, id)
            if (found is Result.Failed) return@run found
            val batch =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_import_not_found")
                    )
            if (batch.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (batch.status != EmployeeImportStatus.REVIEW)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "employee_import_not_ready"))
            val counts = imports.counts(company, id)
            if (counts is Result.Failed) return@run counts
            val summary = (counts as Result.Success).value
            val ready = summary[EmployeeImportRowStatus.READY] ?: 0
            if ((summary[EmployeeImportRowStatus.PENDING] ?: 0) > 0 || ready == 0)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "employee_import_has_no_ready_rows")
                )
            if (!allowPartial && (summary[EmployeeImportRowStatus.INVALID] ?: 0) > 0)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "employee_import_has_invalid_rows")
                )
            val lock = jobs.lockQueue(company)
            if (lock is Result.Failed) return@run lock
            val queuedAssurance = requireEmployeeImportAssurance(actor, security, clock.instant())
            if (queuedAssurance is Result.Failed) return@run queuedAssurance
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val now = clock.instant()
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.EMPLOYEE_IMPORT_APPLY,
                    operationId,
                    mapOf("importId" to id.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    ready + 1,
                )
            jobs
                .create(job)
                .flatMap {
                    imports.transition(
                        actor,
                        batch,
                        EmployeeImportStatus.IMPORTING,
                        EmployeeImportAttempt(
                            job.id,
                            EmployeeImportPhase.APPLY,
                            actor.accountId,
                            now,
                        ),
                    )
                }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "employee_import",
                                    id,
                                    "people.employee_import_confirmed",
                                    mapOf(
                                        "readyRows" to ready.toString(),
                                        "allowPartial" to allowPartial.toString(),
                                        "jobId" to job.id.toString(),
                                    ),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
