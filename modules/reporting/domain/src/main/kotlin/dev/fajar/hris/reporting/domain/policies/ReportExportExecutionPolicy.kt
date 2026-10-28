package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.reporting.domain.entities.*
import java.time.Instant
import java.util.UUID

const val REPORT_EXPORT_JOB_STEPS = REPORT_EXPORT_MAX_PARTS + 2

fun reportExportJobResource(request: JobRequest): Result<UUID> {
    val text = request.values["exportId"]
    if (
        request.kind != JobKind.REPORT_EXPORT ||
            request.values.keys != setOf("exportId") ||
            text?.length != 36 ||
            request.totalItems != REPORT_EXPORT_JOB_STEPS ||
            request.progressMode != JobProgressMode.UPPER_BOUND
    )
        return exportExecutionFailure("job_scope_mismatch", FailureKind.FORBIDDEN)
    return try {
        val id = UUID.fromString(text)
        if (id.toString() != text)
            exportExecutionFailure("job_scope_mismatch", FailureKind.FORBIDDEN)
        else Result.Success(id)
    } catch (_: IllegalArgumentException) {
        exportExecutionFailure("job_scope_mismatch", FailureKind.FORBIDDEN)
    }
}

fun validateReportExportJobActor(actor: Actor, request: JobRequest): Result<UUID> =
    if (
        actor.companyId != request.companyId ||
            actor.accountId != request.actorId ||
            actor.credentialVersion != request.credentialVersion ||
            actor.authenticatedAt != request.authenticatedAt
    )
        exportExecutionFailure("job_scope_mismatch", FailureKind.FORBIDDEN)
    else reportExportJobResource(request)

fun validateReportExportLifetime(export: ReportExport, at: Instant): Result<Unit> =
    if (export.retiredAt != null || !export.request.expiresAt.isAfter(at))
        exportExecutionFailure("report_export_expired")
    else Result.Success(Unit)

fun reportExportScope(actor: Actor, company: UUID, grants: Set<String>) =
    actor.copy(
        companyId = company,
        permissions = if (company == actor.companyId) actor.permissions intersect grants else grants,
    )

fun validateReportExportSnapshot(
    request: ReportExportRequest,
    snapshot: ReportSnapshot,
): Result<Unit> {
    val c = snapshot.capture
    return if (
        c.id != request.id ||
            c.companyId != request.companyId ||
            c.accountId != request.accountId ||
            c.selection != request.selection ||
            c.dataset != request.dataset ||
            c.schemaVersion != request.schemaVersion ||
            c.formatVersion != request.formatVersion ||
            c.expiresAt != request.expiresAt ||
            c.evaluatedAt < request.createdAt
    )
        exportExecutionFailure("report_snapshot_capture_changed")
    else validateReportExportCapture(c).map { Unit }
}

fun exportChanged(changed: Boolean, code: String = "stale_version"): Result<Unit> =
    if (changed) Result.Success(Unit) else exportExecutionFailure(code)

fun exportExecutionFailure(code: String, kind: FailureKind = FailureKind.CONFLICT) =
    Result.Failed(Failure(kind, code))
