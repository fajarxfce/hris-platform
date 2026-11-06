package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Duration
import java.time.Instant

fun validateCollaborationSummaryCursor(
    cursor: CollaborationSummaryCursor,
    scope: CollaborationSummaryScope,
    head: CollaborationSummaryHead,
    phase: CollaborationSummaryPhase,
    now: Instant,
): Result<Unit> {
    if (cursor.scope.owner != scope.owner || cursor.phase != phase) return invalidSummaryCursor()
    if (cursor.scope != scope || cursor.epoch != head.epoch)
        return Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_summary_scope_changed"))
    val timing = validateCollaborationSummaryCursorTime(cursor, now)
    if (timing is Result.Failed) return timing
    if (head.position < 0 || head.prunedThrough !in 0..head.position)
        return Result.Failed(Failure(FailureKind.UNEXPECTED, "collaboration_summary_head_invalid"))
    if (
        cursor.position < head.prunedThrough ||
            cursor.position > head.position ||
            (cursor.upperPosition ?: cursor.position) > head.position
    )
        return Result.Failed(
            Failure(FailureKind.CONFLICT, "collaboration_summary_cursor_out_of_range")
        )
    if (
        cursor.position < 0 ||
            cursor.page !in 0..999 ||
            cursor.scope.credentialVersion < 0 ||
            cursor.scope.companyVersion < 0 ||
            cursor.scope.membershipVersion < 0 ||
            cursor.upperPosition?.let { it < cursor.position } == true ||
            (phase == CollaborationSummaryPhase.BOOTSTRAP && cursor.upperPosition != null) ||
            (phase == CollaborationSummaryPhase.CHANGES &&
                (cursor.after != null || cursor.page != 0))
    )
        return invalidSummaryCursor()
    return Result.Success(Unit)
}

fun validateCollaborationSummaryCursorTime(
    cursor: CollaborationSummaryCursor,
    now: Instant,
): Result<Unit> {
    val maximum =
        if (cursor.phase == CollaborationSummaryPhase.BOOTSTRAP) Duration.ofMinutes(15)
        else Duration.ofDays(7)
    if (
        !cursor.expiresAt.isAfter(cursor.issuedAt) ||
            Duration.between(cursor.issuedAt, cursor.expiresAt) > maximum ||
            cursor.issuedAt.isAfter(now.plusSeconds(30))
    )
        return invalidSummaryCursor()
    if (!cursor.expiresAt.isAfter(now))
        return Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_summary_cursor_expired"))
    return Result.Success(Unit)
}

private fun invalidSummaryCursor() =
    Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_sync_cursor"))
