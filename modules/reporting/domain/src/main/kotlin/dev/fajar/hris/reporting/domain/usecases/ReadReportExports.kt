package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.JobKind
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.*
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.security.MessageDigest
import java.time.Clock
import java.util.HexFormat
import java.util.UUID

/** Owned status and current-authorized content have independent access requirements. */
class ReadReportExports(
    private val exports: ReportExportRepository,
    private val snapshots: ReportSnapshotRepository,
    private val jobs: JobRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val storage: ObjectStorageRepository,
    private val transactions: TransactionRunner,
    private val sources: ReportSourceTransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun list(
        actor: Actor,
        size: Int = 20,
        before: ReportExportCursor? = null,
    ): Result<ReportExportHistory> {
        if (size !in 1..50 || before?.let { !isReportExportInstant(it.createdAt) } == true)
            return exportExecutionFailure("invalid_pagination", FailureKind.VALIDATION)
        return owned(actor) { company ->
            exports.list(company, actor.accountId, before, size + 1).flatMap { values ->
                val items = mutableListOf<ReportExportDetails>()
                for (value in values.take(size)) {
                    val detail = details(value)
                    if (detail is Result.Failed) return@flatMap detail
                    items += (detail as Result.Success).value
                }
                val next =
                    if (values.size > size)
                        items.last().let { ReportExportCursor(it.createdAt, it.id) }
                    else null
                Result.Success(ReportExportHistory(java.util.List.copyOf(items), next))
            }
        }
    }

    fun get(actor: Actor, id: UUID): Result<ReportExportDetails> =
        owned(actor) { company ->
            exports.find(company, actor.accountId, id).flatMap {
                if (it == null)
                    exportExecutionFailure("report_export_not_found", FailureKind.NOT_FOUND)
                else details(it)
            }
        }

    fun download(actor: Actor, id: UUID, client: ClientRequest): Result<ReportExportDownload> =
        prepare(actor, id, client).map { work ->
            val r = work.export.request
            ReportExportDownload(
                id,
                "report-${r.selection.kind.name.lowercase(java.util.Locale.ROOT)}-$id.csv",
                work.export.byteCount,
                requireNotNull(work.export.manifestTag),
            )
        }

    fun read(
        actor: Actor,
        id: UUID,
        tag: String,
        offset: Long,
        length: Int,
        client: ClientRequest,
    ): Result<ByteArray> {
        if (
            offset !in 0 until REPORT_SNAPSHOT_MAX_BYTES ||
                length !in 1..REPORT_CSV_MAX_PART_BYTES ||
                !tag.matches(Regex("[0-9a-f]{64}"))
        )
            return exportExecutionFailure("invalid_report_export_range", FailureKind.VALIDATION)
        val prepared = prepare(actor, id, client)
        if (prepared is Result.Failed) return prepared
        val work = (prepared as Result.Success).value
        if (work.export.manifestTag != tag) return exportExecutionFailure("report_export_changed")
        if (offset + length > work.export.byteCount)
            return exportExecutionFailure("invalid_report_export_range", FailureKind.VALIDATION)
        val output = ByteArray(length)
        var copied = 0
        // A range can cross many small CSV parts. Each acquired object remains <= one MiB.
        for (part in work.parts) {
            val p = part.upload
            val from = maxOf(offset, p.offset)
            val until = minOf(offset + length, p.offset + p.bytes)
            if (from >= until) continue
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val bytes = storage.read(reportExportObjectKey(p), 0, p.bytes, part.etag)
            if (bytes is Result.Failed) return bytes
            val content = (bytes as Result.Success).value
            if (
                content.size != p.bytes ||
                    HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(content)) != p.sha256
            )
                return exportExecutionFailure("report_export_content_integrity_failure")
            // Also authorizes the next object read; no intervening I/O uses an older access check.
            val allowed =
                guarded(actor, work.export.request, work.grants, client) { current ->
                    if (current.version != work.export.version || current.manifestTag != tag)
                        exportExecutionFailure("report_export_changed")
                    else Result.Success(Unit)
                }
            if (allowed is Result.Failed) return allowed
            val count = (until - from).toInt()
            content.copyInto(
                output,
                (from - offset).toInt(),
                (from - p.offset).toInt(),
                (from - p.offset).toInt() + count,
            )
            copied += count
        }
        return if (copied == length) Result.Success(output)
        else exportExecutionFailure("report_export_manifest_changed")
    }

    private fun prepare(actor: Actor, id: UUID, client: ClientRequest): Result<DownloadWork> {
        val company =
            actor.companyId
                ?: return exportExecutionFailure("company_required", FailureKind.FORBIDDEN)
        val permitted = actor.requirePermission("reports.read")
        if (permitted is Result.Failed) return permitted
        // Capture current original grants before waiting on resource/access guards.
        val observed =
            transactions.run(actor) {
                checkOwner(actor)
                    .flatMap { exports.find(company, actor.accountId, id) }
                    .flatMap { export ->
                        if (export == null)
                            return@flatMap exportExecutionFailure(
                                "report_export_not_found",
                                FailureKind.NOT_FOUND,
                            )
                        val grants = linkedMapOf<UUID, Set<String>>()
                        for ((source, saved) in export.request.sources.toSortedMap()) {
                            val current =
                                identities
                                    .access(actor.accountId, source)
                                    .flatMap {
                                        validateCompanySessionActor(
                                            reportExportScope(actor, source, saved),
                                            it,
                                            clock.instant(),
                                            security,
                                        )
                                    }
                                    .flatMap { live ->
                                        validateReportKindAccess(
                                                live,
                                                export.request.selection.kind,
                                            )
                                            .map { live.permissions }
                                    }
                            if (current is Result.Failed) return@flatMap current
                            grants[source] = (current as Result.Success).value intersect saved
                        }
                        Result.Success(
                            export to
                                java.util.Map.copyOf(
                                    grants.mapValues { java.util.Set.copyOf(it.value) }
                                )
                        )
                    }
            }
        if (observed is Result.Failed) return observed
        val (export, grants) = (observed as Result.Success).value
        return guarded(actor, export.request, grants, client) { current ->
            val loaded = snapshots.find(company, actor.accountId, id)
            if (loaded is Result.Failed) return@guarded loaded
            val snapshot =
                (loaded as Result.Success).value
                    ?: return@guarded exportExecutionFailure("report_snapshot_not_found")
            val valid = validateReportExportSnapshot(current.request, snapshot)
            if (valid is Result.Failed) return@guarded valid
            exports.parts(company, actor.accountId, id).flatMap { parts ->
                reportExportManifest(snapshot, parts).flatMap { manifest ->
                    if (
                        manifest.tag != current.manifestTag ||
                            manifest.bytes != current.byteCount ||
                            manifest.rows != current.completedRows ||
                            parts.size != current.partCount ||
                            parts.any { it.etag.isBlank() || it.etag.length > 200 }
                    )
                        exportExecutionFailure("report_export_manifest_changed")
                    else Result.Success(DownloadWork(current, parts, grants))
                }
            }
        }
    }

    private fun <T> guarded(
        actor: Actor,
        request: ReportExportRequest,
        grants: Map<UUID, Set<String>>,
        client: ClientRequest,
        action: (ReportExport) -> Result<T>,
    ): Result<T> =
        sources.runWithReportSources(actor, reportExportCompanies(request)) {
            val owner = exports.lockOwner(request.companyId, actor.accountId)
            if (owner is Result.Failed) return@runWithReportSources owner
            val loaded = exports.find(request.companyId, actor.accountId, request.id, true)
            if (loaded is Result.Failed) return@runWithReportSources loaded
            val current =
                (loaded as Result.Success).value
                    ?: return@runWithReportSources exportExecutionFailure(
                        "report_export_not_found",
                        FailureKind.NOT_FOUND,
                    )
            if (current.request != request)
                return@runWithReportSources exportExecutionFailure("report_export_changed")
            val ordered = grants.keys.sorted()
            for (company in ordered) {
                val r = policies.lock(company, shared = true)
                if (r is Result.Failed) return@runWithReportSources r
            }
            for (company in ordered) {
                val r = companies.lock(company, shared = true)
                if (r is Result.Failed) return@runWithReportSources r
            }
            for (company in ordered) {
                val r = members.lock(company, shared = true)
                if (r is Result.Failed) return@runWithReportSources r
            }
            val account = identities.lockAccount(actor.accountId, shared = true)
            if (account is Result.Failed) return@runWithReportSources account
            checkContent(actor, current, grants, client)
                .flatMap { action(current) }
                .flatMap { value -> checkContent(actor, current, grants, client).map { value } }
        }

    private fun checkContent(
        actor: Actor,
        current: ReportExport,
        grants: Map<UUID, Set<String>>,
        client: ClientRequest,
    ): Result<Unit> {
        val guardedAccess = linkedMapOf<UUID, AccountAccess>()
        for ((company, permissions) in grants.toSortedMap()) {
            val loaded = identities.access(actor.accountId, company)
            if (loaded is Result.Failed) return loaded
            val access = (loaded as Result.Success).value
            val allowed =
                validateCompanySessionActor(
                        reportExportScope(actor, company, permissions),
                        access,
                        clock.instant(),
                        security,
                    )
                    .flatMap { validateReportKindAccess(it, current.request.selection.kind) }
            if (allowed is Result.Failed) return allowed
            guardedAccess[company] = requireNotNull(access)
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
        val completedAt = clock.instant()
        for ((company, access) in guardedAccess) {
            val assurance =
                validateCompanySessionActor(
                    reportExportScope(actor, company, grants.getValue(company)),
                    access,
                    completedAt,
                    security,
                )
            if (assurance is Result.Failed) return assurance
        }
        val lifetime = validateReportExportLifetime(current, completedAt)
        if (lifetime is Result.Failed) return lifetime
        return if (current.status == ReportExportStatus.READY) Result.Success(Unit)
        else exportExecutionFailure("report_export_not_ready")
    }

    private fun details(export: ReportExport): Result<ReportExportDetails> {
        val r = export.request
        return jobs.find(r.companyId, export.jobId).flatMap { job ->
            if (
                job == null ||
                    job.request.actorId != r.accountId ||
                    job.request.kind != JobKind.REPORT_EXPORT ||
                    job.request.values["exportId"] != r.id.toString()
            )
                exportExecutionFailure("report_export_job_missing")
            else
                Result.Success(
                    ReportExportDetails(
                        r.id,
                        r.companyId,
                        r.selection,
                        r.dataset,
                        export.status,
                        export.version,
                        export.attempt,
                        r.createdAt,
                        export.updatedAt,
                        r.expiresAt,
                        !r.expiresAt.isAfter(clock.instant()) || export.retiredAt != null,
                        export.retiredAt,
                        export.completedRows,
                        export.byteCount,
                        export.jobId,
                        job.status,
                        job.cancellationRequested,
                        export.failureCode ?: job.failureCode,
                    )
                )
        }
    }

    private fun <T> owned(actor: Actor, action: (UUID) -> Result<T>): Result<T> {
        val company =
            actor.companyId
                ?: return exportExecutionFailure("company_required", FailureKind.FORBIDDEN)
        val permitted = actor.requirePermission("reports.read")
        if (permitted is Result.Failed) return permitted
        return transactions.run(actor) {
            val owner = exports.lockOwner(company, actor.accountId)
            if (owner is Result.Failed) return@run owner
            val c = companies.lock(company, shared = true)
            if (c is Result.Failed) return@run c
            val m = members.lock(company, shared = true)
            if (m is Result.Failed) return@run m
            val a = identities.lockAccount(actor.accountId, shared = true)
            if (a is Result.Failed) return@run a
            checkOwner(actor)
                .flatMap { action(company) }
                .flatMap { value -> checkOwner(actor).map { value } }
        }
    }

    private fun checkOwner(actor: Actor) =
        identities.access(actor.accountId, requireNotNull(actor.companyId)).flatMap {
            validateCompanySessionActor(actor, it, clock.instant(), security).flatMap { current ->
                current.requirePermission("reports.read")
            }
        }

    private data class DownloadWork(
        val export: ReportExport,
        val parts: List<ReportExportPart>,
        val grants: Map<UUID, Set<String>>,
    )
}
