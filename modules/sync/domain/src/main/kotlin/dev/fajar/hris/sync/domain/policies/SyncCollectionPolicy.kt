package dev.fajar.hris.sync.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.policies.canReadSelfProfile
import dev.fajar.hris.sync.domain.entities.SyncCollection

// Preserve the established projection for clients that omit collection selection.
private val defaultSyncCollections =
    setOf(
        SyncCollection.EXPENSE_CLAIMS,
        SyncCollection.LEAVE_REQUESTS,
        SyncCollection.LEAVE_BALANCES,
        SyncCollection.OVERTIME_REQUESTS,
        SyncCollection.PAYSLIPS,
        SyncCollection.PAYROLL_PAYMENTS,
    )

/** A client selection can narrow its owned projection; it never grants access. */
fun selectSyncCollections(
    permissions: Set<String>,
    requested: Set<SyncCollection>?,
): Result<Set<SyncCollection>> {
    if (requested?.isEmpty() == true)
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_sync_collections",
                fields = mapOf("collections" to "required"),
            )
        )
    // Existing read grants also admit owned resources. Explicit selection exposes
    // that same projection without changing default feeds or exporting team data.
    val authorized = selfSyncCollections(permissions).toMutableSet()
    if (requested != null) {
        if (canReadSelfProfile(permissions)) authorized += SyncCollection.SELF_PROFILES
        if ("documents.self.read" in permissions) authorized += SyncCollection.DOCUMENT_EVIDENCE
        if (permissions.any { it in setOf("people.directory.read", "people.read") })
            authorized += SyncCollection.DIRECTORY_EMPLOYEES
        if (
            permissions.any {
                it in setOf("expenses.self.manage", "expenses.read", "expenses.manage")
            }
        ) {
            authorized += SyncCollection.EXPENSE_CLAIMS
            authorized += SyncCollection.EXPENSE_PAYMENTS
        }
        if ("leave.read" in permissions) {
            authorized += SyncCollection.LEAVE_REQUESTS
            authorized += SyncCollection.LEAVE_BALANCES
        }
        if ("payroll.read" in permissions) {
            authorized += SyncCollection.PAYSLIPS
            authorized += SyncCollection.PAYROLL_PAYMENTS
        }
        if (permissions.any { it in setOf("overtime.read", "overtime.manage", "workforce.read") })
            authorized += SyncCollection.OVERTIME_REQUESTS
        if ("workforce.read" in permissions) authorized += SyncCollection.ATTENDANCE_DAYS
        if (permissions.any { it in setOf("attendance.self.record", "workforce.read") }) {
            authorized += SyncCollection.WORK_SCHEDULES
            authorized += SyncCollection.ROSTER_DAYS
            authorized += SyncCollection.WORK_HOLIDAYS
        }
    }
    val selected = authorized.intersect(requested ?: defaultSyncCollections)
    if (selected.isEmpty())
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "sync_access_denied"))
    return Result.Success(selected)
}
