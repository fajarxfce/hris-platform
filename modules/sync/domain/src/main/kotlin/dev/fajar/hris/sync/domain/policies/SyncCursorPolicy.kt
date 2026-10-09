package dev.fajar.hris.sync.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.domain.entities.*
import java.time.Duration
import java.time.Instant

fun selfSyncCollections(permissions: Set<String>): Set<SyncCollection> = buildSet {
    if ("expenses.self.manage" in permissions) add(SyncCollection.EXPENSE_CLAIMS)
    if ("leave.self.manage" in permissions) {
        add(SyncCollection.LEAVE_REQUESTS)
        add(SyncCollection.LEAVE_BALANCES)
    }
    if ("payroll.self.read" in permissions) {
        add(SyncCollection.PAYSLIPS)
        add(SyncCollection.PAYROLL_PAYMENTS)
    }
    if ("overtime.self.manage" in permissions) add(SyncCollection.OVERTIME_REQUESTS)
    if ("announcements.read" in permissions) add(SyncCollection.INBOX)
}

fun validateSyncCursor(
    cursor: SyncCursor,
    scope: SyncScope,
    fingerprint: String,
    head: SyncHead,
    phase: SyncCursorPhase,
    now: Instant,
): Result<Unit> {
    if (
        cursor.accountId != scope.accountId ||
            cursor.companyId != scope.companyId ||
            cursor.phase != phase
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_sync_cursor"))
    if (cursor.epoch != head.epoch || cursor.scopeFingerprint != fingerprint)
        return Result.Failed(Failure(FailureKind.CONFLICT, "sync_scope_changed"))
    val temporal = validateSyncCursorTime(cursor, now)
    if (temporal is Result.Failed) return temporal
    if (
        cursor.position < head.prunedThrough ||
            cursor.position > head.position ||
            (cursor.upperPosition ?: cursor.position) > head.position
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "sync_cursor_out_of_range"))
    if (
        cursor.position < 0 ||
            cursor.page !in 0..999 ||
            cursor.upperPosition?.let { it < cursor.position } == true ||
            (phase == SyncCursorPhase.BOOTSTRAP && cursor.upperPosition != null) ||
            (phase == SyncCursorPhase.CHANGES && (cursor.after != null || cursor.page != 0)) ||
            (cursor.after != null && cursor.after.collection !in scope.collections)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_sync_cursor"))
    return Result.Success(Unit)
}

fun validateSyncCursorTime(cursor: SyncCursor, now: Instant): Result<Unit> {
    val maximum =
        if (cursor.phase == SyncCursorPhase.BOOTSTRAP) Duration.ofMinutes(15)
        else Duration.ofDays(7)
    if (
        !cursor.expiresAt.isAfter(cursor.issuedAt) ||
            Duration.between(cursor.issuedAt, cursor.expiresAt) > maximum ||
            cursor.issuedAt.isAfter(now.plusSeconds(30))
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_sync_cursor"))
    if (!cursor.expiresAt.isAfter(now))
        return Result.Failed(Failure(FailureKind.CONFLICT, "sync_cursor_expired"))
    return Result.Success(Unit)
}
