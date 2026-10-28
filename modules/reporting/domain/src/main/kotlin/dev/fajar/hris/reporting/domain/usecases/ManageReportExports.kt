package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.ReportExportRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Interactive lifecycle commands share the same original/live authority and transaction owner. */
class ManageReportExports(
    private val exports: ReportExportRepository,
    private val jobs: JobRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val sources: ReportSourceTransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun start(
        actor: Actor,
        operationId: UUID,
        input: ReportSelection,
        dataset: ReportExportDataset,
        client: ClientRequest,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return exportExecutionFailure("company_required", FailureKind.FORBIDDEN)
        val permitted = validateReportKindAccess(actor, input.kind)
        if (permitted is Result.Failed) return permitted
        val selection = validateReportSelection(company, input)
        if (selection is Result.Failed) return selection
        val normalized = (selection as Result.Success).value
        val columns = reportExportColumns(normalized.kind, dataset)
        if (columns is Result.Failed) return columns
        val selected =
            if (normalized.kind == ReportKind.HEADCOUNT) normalized.parameters.companies.toSet()
            else setOf(company)
        val captured = captureAuthority(actor, selected + company, normalized.kind, null)
        if (captured is Result.Failed) return captured
        val grants = (captured as Result.Success).value
        val p = normalized.parameters
        val key =
            OperationKey(
                "reporting.export_requested",
                operationId,
                listOf(
                    normalized.kind.name,
                    normalized.definitionVersion,
                    p.asOf,
                    p.from,
                    p.until,
                    p.status,
                    p.companies.joinToString(","),
                    dataset.name,
                    "report-export.v1",
                    REPORT_CSV_FORMAT_VERSION,
                ),
            )
        return sources.runWithReportSources(actor, selected) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@runWithReportSources replay
            val owner = exports.lockOwner(company, actor.accountId)
            if (owner is Result.Failed) return@runWithReportSources owner
            val guarded = guardSources(actor, grants.keys)
            if (guarded is Result.Failed) return@runWithReportSources guarded
            val access = checkSources(actor, grants, normalized.kind, client)
            if (access is Result.Failed) return@runWithReportSources access
            (replay as Result.Success).value?.let {
                return@runWithReportSources Result.Success(it)
            }
            val quota = exports.quota(company, actor.accountId)
            if (quota is Result.Failed) return@runWithReportSources quota
            val q = (quota as Result.Success).value
            if (q.active >= REPORT_EXPORT_MAX_ACTIVE)
                return@runWithReportSources exportExecutionFailure("report_export_active_limit")
            if (q.retained >= REPORT_EXPORT_MAX_RETAINED)
                return@runWithReportSources exportExecutionFailure("report_export_retention_limit")
            val available = queue(company)
            if (available is Result.Failed) return@runWithReportSources available
            val stillAllowed = checkSources(actor, grants, normalized.kind, client)
            if (stillAllowed is Result.Failed) return@runWithReportSources stillAllowed
            val at = now()
            // Never derive a cleanup resource identity from caller-controlled command keys.
            val request =
                ReportExportRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    normalized,
                    dataset,
                    grants,
                    client,
                    at,
                    at.plus(REPORT_EXPORT_LIFETIME),
                )
            val valid = validateReportExportRequest(request)
            if (valid is Result.Failed) return@runWithReportSources valid
            val created = (valid as Result.Success).value
            val job = job(actor, created, operationId, at, (stillAllowed as Result.Success).value)
            jobs
                .create(job)
                .flatMap { exports.create(created, job.id) }
                .flatMap { exportChanged(it, "report_export_identity_conflict") }
                .flatMap {
                    finish(
                        actor,
                        key,
                        MutationReceipt(created.id, 0),
                        "reporting.export_requested",
                        job.id,
                    )
                }
                .flatMap { receipt ->
                    checkSources(actor, grants, normalized.kind, client).map { receipt }
                }
        }
    }

    fun resume(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        client: ClientRequest,
    ): Result<MutationReceipt> {
        val observed = observe(actor, id, version)
        if (observed is Result.Failed) return observed
        val old = (observed as Result.Success).value
        val captured =
            captureAuthority(
                actor,
                old.request.sources.keys,
                old.request.selection.kind,
                old.request.sources,
            )
        if (captured is Result.Failed) return captured
        val grants = (captured as Result.Success).value
        val key =
            OperationKey(
                "reporting.export_resumed",
                operationId,
                listOf(id.toString(), version.toString()),
            )
        return sources.runWithReportSources(actor, reportExportCompanies(old.request)) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@runWithReportSources replay
            val previous = jobs.find(old.request.companyId, old.jobId, true)
            if (previous is Result.Failed) return@runWithReportSources previous
            val owner = exports.lockOwner(old.request.companyId, actor.accountId)
            if (owner is Result.Failed) return@runWithReportSources owner
            val found = exports.find(old.request.companyId, actor.accountId, id, true)
            if (found is Result.Failed) return@runWithReportSources found
            val guarded = guardSources(actor, grants.keys)
            if (guarded is Result.Failed) return@runWithReportSources guarded
            val access = checkSources(actor, grants, old.request.selection.kind, client)
            if (access is Result.Failed) return@runWithReportSources access
            (replay as Result.Success).value?.let {
                return@runWithReportSources Result.Success(it)
            }
            val current =
                (found as Result.Success).value
                    ?: return@runWithReportSources exportExecutionFailure(
                        "report_export_not_found",
                        FailureKind.NOT_FOUND,
                    )
            if (
                current.version != version ||
                    current.jobId != old.jobId ||
                    current.request != old.request
            )
                return@runWithReportSources exportExecutionFailure("stale_version")
            val lifetime = validateReportExportLifetime(current, clock.instant())
            if (lifetime is Result.Failed) return@runWithReportSources lifetime
            val stopped = (previous as Result.Success).value
            if (
                stopped == null ||
                    stopped.request.actorId != actor.accountId ||
                    stopped.request.values["exportId"] != id.toString() ||
                    stopped.request.kind != JobKind.REPORT_EXPORT ||
                    stopped.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED) ||
                    current.status == ReportExportStatus.READY
            )
                return@runWithReportSources exportExecutionFailure("report_export_not_stopped")
            if (
                current.attempt >= REPORT_EXPORT_MAX_ATTEMPTS ||
                    current.reservationCount >= REPORT_EXPORT_MAX_RESERVATIONS ||
                    current.version >= REPORT_EXPORT_MAX_VERSION - 2
            )
                return@runWithReportSources exportExecutionFailure("report_export_attempt_limit")
            val counted = exports.quota(old.request.companyId, actor.accountId)
            if (counted is Result.Failed) return@runWithReportSources counted
            val occupied =
                if (
                    current.status in setOf(ReportExportStatus.QUEUED, ReportExportStatus.RENDERING)
                )
                    1
                else 0
            if ((counted as Result.Success).value.active - occupied >= REPORT_EXPORT_MAX_ACTIVE)
                return@runWithReportSources exportExecutionFailure("report_export_active_limit")
            val available = queue(old.request.companyId)
            if (available is Result.Failed) return@runWithReportSources available
            val stillAllowed = checkSources(actor, grants, old.request.selection.kind, client)
            if (stillAllowed is Result.Failed) return@runWithReportSources stillAllowed
            val originatingClient =
                checkSources(actor, grants, old.request.selection.kind, current.request.client)
            if (originatingClient is Result.Failed) return@runWithReportSources originatingClient
            val at = maxOf(now(), current.updatedAt)
            val job =
                job(actor, current.request, operationId, at, (stillAllowed as Result.Success).value)
            jobs
                .create(job)
                .flatMap { exports.resume(current, job.id, at) }
                .flatMap { exportChanged(it) }
                .flatMap {
                    finish(
                        actor,
                        key,
                        MutationReceipt(id, current.version + 1),
                        "reporting.export_resumed",
                        job.id,
                    )
                }
                .flatMap { receipt ->
                    checkSources(actor, grants, old.request.selection.kind, client)
                        .flatMap { validateReportExportLifetime(current, clock.instant()) }
                        .map { receipt }
                }
        }
    }

    /** Owner cancellation remains available after source grants or module availability are lost. */
    fun cancel(actor: Actor, operationId: UUID, id: UUID, version: Long): Result<MutationReceipt> {
        val observed = observe(actor, id, version)
        if (observed is Result.Failed) return observed
        val old = (observed as Result.Success).value
        val key =
            OperationKey(
                "reporting.export_cancelled",
                operationId,
                listOf(id.toString(), version.toString()),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val job = jobs.find(old.request.companyId, old.jobId, true)
            if (job is Result.Failed) return@run job
            val owner = exports.lockOwner(old.request.companyId, actor.accountId)
            if (owner is Result.Failed) return@run owner
            val found = exports.find(old.request.companyId, actor.accountId, id, true)
            if (found is Result.Failed) return@run found
            val company = companies.lock(old.request.companyId, shared = true)
            if (company is Result.Failed) return@run company
            val member = members.lock(old.request.companyId, shared = true)
            if (member is Result.Failed) return@run member
            val account = identities.lockAccount(actor.accountId, shared = true)
            if (account is Result.Failed) return@run account
            val access = checkOwner(actor)
            if (access is Result.Failed) return@run access
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val current =
                (found as Result.Success).value
                    ?: return@run exportExecutionFailure(
                        "report_export_not_found",
                        FailureKind.NOT_FOUND,
                    )
            if (current.version != version || current.jobId != old.jobId)
                return@run exportExecutionFailure("stale_version")
            if (
                current.status !in setOf(ReportExportStatus.QUEUED, ReportExportStatus.RENDERING) ||
                    current.retiredAt != null
            )
                return@run exportExecutionFailure("report_export_not_running")
            val background =
                (job as Result.Success).value
                    ?: return@run exportExecutionFailure("report_export_job_missing")
            if (
                background.request.actorId != actor.accountId ||
                    background.request.kind != JobKind.REPORT_EXPORT ||
                    background.request.values["exportId"] != id.toString()
            )
                return@run exportExecutionFailure("job_scope_mismatch", FailureKind.FORBIDDEN)
            val at = maxOf(now(), current.updatedAt)
            if (
                background.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING) &&
                    !background.cancellationRequested
            ) {
                val cancelled =
                    jobs.requestCancellation(
                        old.request.companyId,
                        background.request.id,
                        background.version,
                        at,
                    )
                if (cancelled is Result.Failed) return@run cancelled
                if ((cancelled as Result.Success).value == null)
                    return@run exportExecutionFailure("stale_version")
            }
            exports
                .stop(current, "report_export_cancelled", at)
                .flatMap { exportChanged(it) }
                .flatMap {
                    finish(
                        actor,
                        key,
                        MutationReceipt(id, current.version + 1),
                        "reporting.export_cancelled",
                        current.jobId,
                    )
                }
                .flatMap { receipt -> checkOwner(actor).map { receipt } }
        }
    }

    private fun observe(actor: Actor, id: UUID, version: Long): Result<ReportExport> {
        val company =
            actor.companyId
                ?: return exportExecutionFailure("company_required", FailureKind.FORBIDDEN)
        val permitted = actor.requirePermission("reports.read")
        if (permitted is Result.Failed) return permitted
        if (version !in 0..REPORT_EXPORT_MAX_VERSION)
            return exportExecutionFailure("invalid_version", FailureKind.VALIDATION)
        return transactions.run(actor) {
            checkOwner(actor)
                .flatMap { exports.find(company, actor.accountId, id) }
                .flatMap { value ->
                    if (value == null)
                        exportExecutionFailure("report_export_not_found", FailureKind.NOT_FOUND)
                    else checkOwner(actor).map { value }
                }
        }
    }

    private fun captureAuthority(
        actor: Actor,
        selected: Set<UUID>,
        kind: ReportKind,
        original: Map<UUID, Set<String>>?,
    ): Result<Map<UUID, Set<String>>> =
        transactions.run(actor) {
            val grants = linkedMapOf<UUID, Set<String>>()
            for (company in selected.sorted()) {
                val loaded = identities.access(actor.accountId, company)
                if (loaded is Result.Failed) return@run loaded
                val access =
                    (loaded as Result.Success).value
                        ?: return@run exportExecutionFailure(
                            "session_revoked",
                            FailureKind.UNAUTHENTICATED,
                        )
                val initial = original?.getValue(company) ?: access.permissions
                val checked =
                    validateCompanySessionActor(
                            reportExportScope(actor, company, initial),
                            access,
                            clock.instant(),
                            security,
                        )
                        .flatMap { live -> validateReportKindAccess(live, kind).map { live } }
                if (checked is Result.Failed) return@run checked
                grants[company] =
                    (checked as Result.Success).value.permissions intersect
                        reportExportSourcePermissions(kind)
            }
            Result.Success(
                java.util.Map.copyOf(grants.mapValues { java.util.Set.copyOf(it.value) })
            )
        }

    private fun guardSources(actor: Actor, selected: Set<UUID>): Result<Unit> {
        val ordered = selected.sorted()
        for (company in ordered) {
            val r = policies.lock(company, shared = true)
            if (r is Result.Failed) return r
        }
        for (company in ordered) {
            val r = companies.lock(company, shared = true)
            if (r is Result.Failed) return r
        }
        for (company in ordered) {
            val r = members.lock(company, shared = true)
            if (r is Result.Failed) return r
        }
        return identities.lockAccount(actor.accountId, shared = true)
    }

    private fun checkSources(
        actor: Actor,
        grants: Map<UUID, Set<String>>,
        kind: ReportKind,
        client: ClientRequest,
    ): Result<Long> {
        var credentials: Long? = null
        for ((company, permissions) in grants.toSortedMap()) {
            val loaded = identities.access(actor.accountId, company)
            if (loaded is Result.Failed) return loaded
            val access = (loaded as Result.Success).value
            val checked =
                validateCompanySessionActor(
                        reportExportScope(actor, company, permissions),
                        access,
                        clock.instant(),
                        security,
                    )
                    .flatMap { validateReportKindAccess(it, kind) }
            if (checked is Result.Failed) return checked
            if (company == actor.companyId)
                credentials = requireNotNull(access).account.securityVersion
            val at = clock.instant()
            val available =
                policies.effective(company, at).flatMap {
                    validateClientAvailability(
                        clientPolicyStatus(it, at),
                        setOf(CompanyModule.REPORTING),
                        client.version,
                        client.native,
                    )
                }
            if (available is Result.Failed)
                return Result.Failed(
                    available.failure.copy(
                        parameters =
                            available.failure.parameters + ("companyId" to company.toString())
                    )
                )
        }
        return Result.Success(requireNotNull(credentials))
    }

    private fun checkOwner(actor: Actor) =
        identities.access(actor.accountId, requireNotNull(actor.companyId)).flatMap {
            validateCompanySessionActor(actor, it, clock.instant(), security).flatMap { live ->
                live.requirePermission("reports.read")
            }
        }

    private fun queue(company: UUID): Result<Unit> =
        jobs
            .lockQueue(company)
            .flatMap { jobs.pendingCount(company) }
            .flatMap {
                if (it >= 100) exportExecutionFailure("job_queue_full") else Result.Success(Unit)
            }

    private fun job(
        actor: Actor,
        request: ReportExportRequest,
        operationId: UUID,
        at: Instant,
        credentials: Long,
    ) =
        JobRequest(
            UUID.randomUUID(),
            request.companyId,
            actor.accountId,
            JobKind.REPORT_EXPORT,
            operationId,
            mapOf("exportId" to request.id.toString()),
            actor.authenticatedAt,
            credentials,
            actor.correlationId,
            at,
            REPORT_EXPORT_JOB_STEPS,
            JobProgressMode.UPPER_BOUND,
        )

    private fun finish(
        actor: Actor,
        key: OperationKey,
        receipt: MutationReceipt,
        action: String,
        job: UUID,
    ) =
        journal
            .record(
                actor,
                ChangeRecord(
                    "report_export",
                    receipt.id,
                    action,
                    mapOf("jobId" to job.toString(), "version" to receipt.version.toString()),
                ),
            )
            .flatMap { operations.record(actor, key, receipt) }
            .map { receipt }

    private fun now() = clock.instant().truncatedTo(ChronoUnit.MICROS)
}
