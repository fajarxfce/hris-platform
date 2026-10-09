package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.entities.UnitKind
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.LocalDate

class AdvanceEmployeeImportPreview(
    private val imports: EmployeeImportRepository,
    private val companies: CompanyRepository,
    private val jobs: JobRepository,
    private val people: PeopleRepository,
    private val units: OrganizationRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val request = lease.job.request
        if (
            request.kind != JobKind.EMPLOYEE_IMPORT_PREVIEW ||
                actor.companyId != request.companyId ||
                actor.accountId != request.actorId
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
        val company = request.companyId
        return transactions.run(actor) {
            val current = jobs.lockLease(lease)
            if (current is Result.Failed) return@run current
            val job =
                (current as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            if (job.cancellationRequested)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested")
                )
            val found = imports.forJob(company, request.id, true)
            if (found is Result.Failed) return@run found
            val batch = (found as Result.Success).value
            if (
                batch == null ||
                    batch.status != EmployeeImportStatus.PREVIEWING ||
                    batch.id.toString() != request.values["importId"]
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "employee_import_job_obsolete")
                )
            val structure = people.lockReportingLines(company)
            if (structure is Result.Failed) return@run structure
            val unitLock = units.lockStructure(company)
            if (unitLock is Result.Failed) return@run unitLock
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val actorLock = identities.lockAccount(actor.accountId, shared = true)
            if (actorLock is Result.Failed) return@run actorLock
            val authorization = identities.access(actor.accountId, company)
            if (authorization is Result.Failed) return@run authorization
            val live = validateEmployeeImportActor(actor, (authorization as Result.Success).value)
            if (live is Result.Failed) return@run live
            val cursor = parseEmployeeImportCursor(job.checkpoint)
            if (cursor is Result.Failed) return@run cursor
            val next =
                imports.nextRow(
                    company,
                    batch.id,
                    EmployeeImportRowStatus.PENDING,
                    (cursor as Result.Success).value,
                )
            if (next is Result.Failed) return@run next
            val row = (next as Result.Success).value
            if (row == null) {
                val counts = imports.counts(company, batch.id)
                if (counts is Result.Failed) return@run counts
                if (
                    ((counts as Result.Success).value[EmployeeImportRowStatus.PENDING] ?: 0) != 0 ||
                        job.completedItems != request.totalItems - 1
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "employee_import_checkpoint_incomplete")
                    )
                return@run jobs
                    .checkpoint(lease, JobProgress(request.totalItems, job.checkpoint))
                    .flatMap { changed ->
                        if (!changed)
                            return@flatMap Result.Failed(
                                Failure(FailureKind.CONFLICT, "job_lease_lost")
                            )
                        imports
                            .transition(actor, batch, EmployeeImportStatus.REVIEW)
                            .flatMap { jobs.complete(lease, JobStatus.SUCCEEDED) }
                            .flatMap { finished ->
                                if (!finished)
                                    Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                                else
                                    journal.record(
                                        actor,
                                        ChangeRecord(
                                            "employee_import",
                                            batch.id,
                                            "people.employee_import_previewed",
                                            mapOf("jobId" to request.id.toString()),
                                        ),
                                    )
                            }
                            .map { JobStep(request.totalItems, true) }
                    }
            }
            val issues = (row.parseIssues + row.issues).toMutableMap()
            val draft = row.draft
            if (draft == null && issues.isEmpty())
                return@run Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "employee_import_proposal_missing")
                )
            if (draft != null) {
                issues.putAll(validateEmployeeDraft(draft, LocalDate.now(clock), batch.reason))
                if (issues.isEmpty()) {
                    val occupied = people.employeeNumberExists(company, draft.employeeNumber)
                    if (occupied is Result.Failed) return@run occupied
                    if ((occupied as Result.Success).value)
                        issues["employeeNumber"] = "employee_number_exists"
                    for ((unitId, reference) in
                        listOf(
                            draft.terms.branchId to ("branchId" to UnitKind.BRANCH),
                            draft.terms.departmentId to ("departmentId" to UnitKind.DEPARTMENT),
                            draft.terms.positionId to ("positionId" to UnitKind.POSITION),
                            draft.terms.costCenterId to ("costCenterId" to UnitKind.COST_CENTER),
                        )) {
                        if (unitId == null) continue
                        val result = units.find(company, unitId)
                        if (result is Result.Failed) return@run result
                        val unit = (result as Result.Success).value
                        if (unit == null || !unit.active || unit.kind != reference.second)
                            issues[reference.first] = "organization_assignment_unavailable"
                    }
                    val managerId = draft.terms.managerId
                    if (managerId != null) {
                        val manager = people.find(company, managerId, draft.terms.startDate)
                        if (manager is Result.Failed) return@run manager
                        if (
                            (manager as Result.Success)
                                .value
                                ?.terms
                                ?.isWorkingOn(draft.terms.startDate) != true
                        )
                            issues["managerId"] = "manager_unavailable"
                    }
                }
            }
            val status =
                if (issues.isEmpty()) EmployeeImportRowStatus.READY
                else EmployeeImportRowStatus.INVALID
            val recorded =
                imports.recordOutcome(
                    company,
                    batch.id,
                    row.number,
                    EmployeeImportRowStatus.PENDING,
                    status,
                    issues.toMap(),
                )
            if (recorded is Result.Failed) return@run recorded
            val progress =
                JobProgress(job.completedItems + 1, mapOf("afterRow" to row.number.toString()))
            jobs.checkpoint(lease, progress).flatMap { changed ->
                if (changed) Result.Success(JobStep(progress.completedItems, false))
                else Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            }
        }
    }
}
