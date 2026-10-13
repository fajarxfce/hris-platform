package dev.fajar.hris.sync.domain.policies

import dev.fajar.hris.core.domain.*
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
        if ("leave.read" in permissions) {
            authorized += SyncCollection.LEAVE_REQUESTS
            authorized += SyncCollection.LEAVE_BALANCES
        }
        if (permissions.any { it in setOf("overtime.read", "overtime.manage", "workforce.read") })
            authorized += SyncCollection.OVERTIME_REQUESTS
    }
    val selected = authorized.intersect(requested ?: defaultSyncCollections)
    if (selected.isEmpty())
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "sync_access_denied"))
    return Result.Success(selected)
}
