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
import java.security.MessageDigest
import java.time.Clock
import java.util.HexFormat
import java.util.UUID

class StartEmployeeImport(
    private val input: EmployeeImportInputRepository,
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
        fileName: String,
        csv: String,
        reason: String,
    ): Result<MutationReceipt> {
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
        val assurance = requireEmployeeImportAssurance(actor, security, clock.instant())
        if (assurance is Result.Failed) return assurance
        if (
            fileName.isBlank() ||
                fileName.length > 120 ||
                fileName.any { it.isISOControl() || it == '/' || it == '\\' } ||
                !fileName.endsWith(".csv", ignoreCase = true) ||
                csv.length > 524288 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employee_import"))
        val bytes = csv.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > 524288)
            return Result.Failed(Failure(FailureKind.VALIDATION, "employee_import_size_limit"))
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "people.employee_import_start",
                operationId,
                listOf(id.toString(), fileName, hash, reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
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
            val decoded = input.decodeCsv(csv)
            if (decoded is Result.Failed) return@run decoded
            val rows =
                (decoded as Result.Success).value.map { row ->
                    val normalized =
                        row.draft?.let { draft ->
                            draft.copy(
                                employeeNumber = draft.employeeNumber.trim().uppercase(),
                                person =
                                    draft.person.copy(
                                        legalName = draft.person.legalName.trim(),
                                        nationality = draft.person.nationality.uppercase(),
                                        email = draft.person.email?.trim()?.lowercase(),
                                    ),
                            )
                        }
                    row.copy(
                        employeeNumber =
                            normalized?.employeeNumber ?: row.employeeNumber.trim().uppercase(),
                        legalName = normalized?.person?.legalName ?: row.legalName.trim(),
                        draft = normalized,
                    )
                }
            val lock = jobs.lockQueue(company)
            if (lock is Result.Failed) return@run lock
            val queuedAssurance = requireEmployeeImportAssurance(actor, security, clock.instant())
            if (queuedAssurance is Result.Failed) return@run queuedAssurance
            val count = jobs.pendingCount(company)
            if (count is Result.Failed) return@run count
            if ((count as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val now = clock.instant()
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.EMPLOYEE_IMPORT_PREVIEW,
                    operationId,
                    mapOf("importId" to id.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    rows.size + 1,
                )
            val batch =
                EmployeeImport(
                    id,
                    fileName,
                    hash,
                    rows.size,
                    EmployeeImportStatus.PREVIEWING,
                    job.id,
                    0,
                    actor.accountId,
                    now,
                    reason,
                )
            jobs
                .create(job)
                .flatMap { imports.create(actor, batch, annotateEmployeeImportDuplicates(rows)) }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "employee_import",
                                    id,
                                    "people.employee_import_started",
                                    mapOf(
                                        "rows" to rows.size.toString(),
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
