package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

const val REPORT_EXPORT_MAX_ACTIVE = 2
const val REPORT_EXPORT_MAX_RETAINED = 10
const val REPORT_EXPORT_MAX_ATTEMPTS = 8
const val REPORT_EXPORT_MAX_PARTS = 1024
const val REPORT_EXPORT_MAX_RESERVATIONS = 1088
const val REPORT_EXPORT_MAX_RESERVED_BYTES = 256L * 1024 * 1024
const val REPORT_EXPORT_MAX_VERSION = 9999L
val REPORT_EXPORT_LIFETIME: Duration = Duration.ofDays(7)
val REPORT_EXPORT_DELETE_GRACE: Duration = Duration.ofHours(1)

fun reportExportSourcePermissions(kind: ReportKind): Set<String> =
    setOf("reports.read") +
        when (kind) {
            ReportKind.HEADCOUNT -> setOf("people.read")
            ReportKind.ATTENDANCE_EXCEPTIONS,
            ReportKind.ATTENDANCE_CLOSINGS -> setOf("workforce.read")
            ReportKind.LEAVE -> setOf("leave.read")
            ReportKind.OVERTIME -> setOf("overtime.read", "overtime.manage", "workforce.read")
            ReportKind.EXPENSES -> setOf("expenses.read", "expenses.manage")
            ReportKind.PAYROLL_COSTS -> setOf("payroll.read")
        }

fun reportExportCompanies(request: ReportExportRequest): Set<UUID> =
    if (request.selection.kind == ReportKind.HEADCOUNT)
        java.util.Set.copyOf(request.selection.parameters.companies)
    else setOf(request.companyId)

fun validateReportExportRequest(input: ReportExportRequest): Result<ReportExportRequest> {
    val invalid = exportFailure("invalid_report_export")
    if (input.sources.size !in 1..33 || input.sources.values.any { it.size !in 1..4 })
        return invalid
    val selection = validateReportSelection(input.companyId, input.selection)
    if (selection is Result.Failed) return selection
    val s = (selection as Result.Success).value
    val columns = reportExportColumns(s.kind, input.dataset)
    if (columns is Result.Failed) return columns
    val capture =
        ReportSnapshotCapture(
            input.id,
            input.companyId,
            input.accountId,
            s,
            input.dataset,
            (columns as Result.Success).value,
            input.createdAt,
            input.expiresAt,
            input.schemaVersion,
            input.formatVersion,
        )
    val valid = validateReportExportCapture(capture)
    if (valid is Result.Failed) return valid
    val request = input.copy(selection = s)
    val companies = reportExportCompanies(request) + request.companyId
    if (input.sources.keys != companies) return invalid
    val allowed = reportExportSourcePermissions(s.kind)
    for ((company, grants) in input.sources) {
        if (!allowed.containsAll(grants)) return invalid
        val actor = Actor(input.accountId, company, grants, input.createdAt, input.id)
        if (validateReportKindAccess(actor, s.kind) is Result.Failed) return invalid
    }
    val v = input.client.version
    if (
        v != null &&
            (v.build !in 0..999999999 ||
                (if (input.client.native) v.platform == ClientPlatform.WEB
                else v.platform != ClientPlatform.WEB))
    )
        return invalid
    return Result.Success(
        request.copy(
            sources =
                java.util.Map.copyOf(
                    input.sources.toSortedMap().mapValues { java.util.Set.copyOf(it.value) }
                )
        )
    )
}

fun validateReportExport(value: ReportExport): Result<ReportExport> =
    validateReportExportRequest(value.request).flatMap { request ->
        val invalid = exportFailure("invalid_report_export_state")
        if (
            value.attempt !in 1..REPORT_EXPORT_MAX_ATTEMPTS ||
                value.version !in 0..REPORT_EXPORT_MAX_VERSION ||
                !isReportExportInstant(value.updatedAt) ||
                value.updatedAt < request.createdAt ||
                value.snapshotId?.let { it != request.id } == true ||
                value.completedRows !in 0..REPORT_SNAPSHOT_MAX_ROWS ||
                value.partCount !in 0..REPORT_EXPORT_MAX_PARTS ||
                value.byteCount !in 0..REPORT_SNAPSHOT_MAX_BYTES ||
                value.reservationCount !in value.partCount..REPORT_EXPORT_MAX_RESERVATIONS ||
                value.reservedBytes !in value.byteCount..REPORT_EXPORT_MAX_RESERVED_BYTES ||
                (value.partCount == 0 && value.completedRows != 0) ||
                (value.partCount == 0) != (value.byteCount == 0L) ||
                (value.reservationCount == 0) != (value.reservedBytes == 0L) ||
                (value.snapshotId == null &&
                    (value.completedRows != 0 || value.reservationCount != 0)) ||
                (value.status == ReportExportStatus.QUEUED && value.snapshotId != null) ||
                (value.status in setOf(ReportExportStatus.RENDERING, ReportExportStatus.READY) &&
                    value.snapshotId == null) ||
                (value.status == ReportExportStatus.STOPPED) != (value.failureCode != null) ||
                value.failureCode?.let { !it.matches(Regex("[a-z][a-z0-9_]{0,79}")) } == true ||
                (value.status == ReportExportStatus.READY) != (value.manifestTag != null) ||
                value.manifestTag?.let { !it.matches(Regex("[0-9a-f]{64}")) } == true ||
                (value.status in setOf(ReportExportStatus.READY, ReportExportStatus.STOPPED)) !=
                    (value.finishedAt != null) ||
                value.finishedAt?.let {
                    !isReportExportInstant(it) || it < request.createdAt || it > value.updatedAt
                } == true ||
                (value.status == ReportExportStatus.READY && value.partCount == 0) ||
                value.retiredAt?.let {
                    !isReportExportInstant(it) ||
                        it < request.expiresAt ||
                        it > value.updatedAt ||
                        value.finishedAt == null
                } == true
        )
            invalid
        else Result.Success(value.copy(request = request))
    }

fun reportExportObjectKey(upload: ReportExportUpload) =
    "${upload.companyId}/reports/${upload.exportId}/${upload.id}"

fun validateReportExportUpload(value: ReportExportUpload): Result<ReportExportUpload> =
    if (
        value.number !in 1..REPORT_EXPORT_MAX_PARTS ||
            value.afterRow !in 0..REPORT_SNAPSHOT_MAX_ROWS ||
            value.rows !in 0..REPORT_CSV_MAX_PART_ROWS ||
            value.afterRow.toLong() + value.rows > REPORT_SNAPSHOT_MAX_ROWS ||
            value.offset !in 0 until REPORT_SNAPSHOT_MAX_BYTES ||
            value.bytes !in 1..REPORT_CSV_MAX_PART_BYTES ||
            value.offset + value.bytes > REPORT_SNAPSHOT_MAX_BYTES ||
            (value.number == 1) != (value.offset == 0L) ||
            (value.number == 1) != (value.afterRow == 0) ||
            (value.rows == 0 && value.number != 1) ||
            !value.sha256.matches(Regex("[0-9a-f]{64}")) ||
            !isReportExportInstant(value.createdAt)
    )
        exportFailure("invalid_report_export_part")
    else Result.Success(value)

fun validateReportExportPart(value: ReportExportPart): Result<ReportExportPart> =
    validateReportExportUpload(value.upload).flatMap {
        if (value.etag.length !in 1..512 || value.etag.any { it.isISOControl() })
            exportFailure("invalid_report_export_part")
        else Result.Success(value)
    }

/** The tag hashes an ordered manifest, not the bytes of the concatenated CSV file. */
fun reportExportManifest(
    snapshot: ReportSnapshot,
    parts: List<ReportExportPart>,
): Result<ReportExportManifest> {
    val invalid = exportFailure("invalid_report_export_manifest")
    if (parts.size !in 1..REPORT_EXPORT_MAX_PARTS) return invalid
    if (
        validateReportExportCapture(snapshot.capture) is Result.Failed ||
            snapshot.rows !in 0..REPORT_SNAPSHOT_MAX_ROWS ||
            snapshot.storedBytes !in 0..REPORT_SNAPSHOT_MAX_BYTES ||
            (snapshot.rows == 0) != (snapshot.storedBytes == 0L)
    )
        return invalid
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("report-manifest.v1\n".toByteArray(Charsets.UTF_8))
    var rows = 0
    var bytes = 0L
    val ids = HashSet<UUID>()
    for ((index, part) in parts.withIndex()) {
        val p = part.upload
        val c = snapshot.capture
        if (
            validateReportExportPart(part) is Result.Failed ||
                p.companyId != c.companyId ||
                p.accountId != c.accountId ||
                p.exportId != c.id ||
                p.number != index + 1 ||
                p.afterRow != rows ||
                p.offset != bytes ||
                !ids.add(p.id) ||
                (p.rows == 0 && (snapshot.rows != 0 || parts.size != 1)) ||
                p.createdAt < c.evaluatedAt ||
                p.createdAt >= c.expiresAt
        )
            return invalid
        rows += p.rows
        bytes += p.bytes
        if (rows > snapshot.rows || bytes > REPORT_SNAPSHOT_MAX_BYTES) return invalid
        digest.update(
            "${p.number}|${p.id}|${p.afterRow}|${p.rows}|${p.offset}|${p.bytes}|${p.sha256}|${part.etag.length}:${part.etag}\n"
                .toByteArray(Charsets.UTF_8)
        )
    }
    return if (rows != snapshot.rows) invalid
    else
        Result.Success(ReportExportManifest(rows, bytes, HexFormat.of().formatHex(digest.digest())))
}

fun isReportExportInstant(at: Instant) =
    at.epochSecond in -62135596800L..253402300799L && at.nano % 1000 == 0

private fun exportFailure(code: String) = Result.Failed(Failure(FailureKind.VALIDATION, code))
