package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.workforce.domain.entities.*
import java.security.MessageDigest
import java.time.LocalDate
import java.util.HexFormat
import java.util.UUID

/** Immutable rows are identified by their IDs; mutable heads contribute their observed version. */
fun attendanceAdjustmentRevision(
    companyId: UUID,
    employee: WorkforceEmployeeReference,
    period: WorkPeriod,
    date: LocalDate,
    adjustmentRevision: Long,
    correction: AttendanceCorrection?,
    entries: List<AttendanceEntry>,
): String {
    val fields = buildList {
        add("hris-attendance-adjustment-v1")
        add(companyId.toString())
        add(employee.id.toString())
        add(employee.version.toString())
        add(period.id.toString())
        add(period.version.toString())
        add(period.jobId.toString())
        add(date.toString())
        add(adjustmentRevision.toString())
        add(correction?.id?.toString() ?: "")
        add(correction?.version?.toString() ?: "")
        for (entry in entries.sortedBy { it.capture.id.toString() }) {
            add(entry.capture.id.toString())
            add(entry.version.toString())
        }
    }
    val digest = MessageDigest.getInstance("SHA-256")
    for (field in fields) {
        val bytes = field.toByteArray(Charsets.UTF_8)
        digest.update("${bytes.size}:".toByteArray(Charsets.US_ASCII))
        digest.update(bytes)
    }
    return HexFormat.of().formatHex(digest.digest())
}
