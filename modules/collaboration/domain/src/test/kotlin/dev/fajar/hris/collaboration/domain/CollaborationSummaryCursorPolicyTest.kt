package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSummaryCursorPolicyTest {
    private val now = Instant.parse("2026-11-05T00:00:00Z")
    private val scope =
        CollaborationSummaryScope(
            CollaborationSummaryOwner(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            2,
            3,
            4,
        )
    private val head = CollaborationSummaryHead(UUID.randomUUID(), 40, 10)
    private val cursor =
        CollaborationSummaryCursor(
            scope,
            head.epoch,
            CollaborationSummaryPhase.CHANGES,
            10,
            now,
            now.plusSeconds(604800),
            30,
        )

    private fun check(
        value: CollaborationSummaryCursor = cursor,
        current: CollaborationSummaryScope = scope,
        position: CollaborationSummaryHead = head,
        at: Instant = now,
        phase: CollaborationSummaryPhase = CollaborationSummaryPhase.CHANGES,
    ) = validateCollaborationSummaryCursor(value, current, position, phase, at)

    private fun code(result: Result<Unit>) = (result as Result.Failed).failure.code

    @Test
    fun retainedFloorAndCapturedUpperBoundRemainIndependentOfLaterPublications() {
        assertEquals(Result.Success(Unit), check())
        assertEquals(Result.Success(Unit), check(position = head.copy(position = 100)))
        assertEquals(
            "collaboration_summary_cursor_out_of_range",
            code(check(position = head.copy(prunedThrough = 11))),
        )
        assertEquals(
            "collaboration_summary_cursor_out_of_range",
            code(check(cursor.copy(upperPosition = 41))),
        )
        assertEquals(
            "collaboration_summary_scope_changed",
            code(check(position = head.copy(epoch = UUID.randomUUID()))),
        )
        assertEquals(
            "collaboration_summary_head_invalid",
            code(check(position = head.copy(prunedThrough = 41))),
        )
    }

    @Test
    fun changedAdmissionScopeAndForeignParentsCannotReuseProgress() {
        for (changed in
            listOf(
                scope.copy(credentialVersion = 3),
                scope.copy(companyVersion = 4),
                scope.copy(membershipVersion = 5),
            )) assertEquals("collaboration_summary_scope_changed", code(check(current = changed)))
        assertEquals(
            "invalid_collaboration_sync_cursor",
            code(
                check(current = scope.copy(owner = scope.owner.copy(parentId = UUID.randomUUID())))
            ),
        )
        assertEquals(
            "invalid_collaboration_sync_cursor",
            code(check(cursor.copy(phase = CollaborationSummaryPhase.BOOTSTRAP))),
        )
    }

    @Test
    fun expiryBootstrapAndMalformedProgressAreBounded() {
        assertEquals(Result.Success(Unit), check(at = cursor.expiresAt.minusNanos(1)))
        assertEquals("collaboration_summary_cursor_expired", code(check(at = cursor.expiresAt)))
        assertEquals(
            "invalid_collaboration_sync_cursor",
            code(check(cursor.copy(expiresAt = cursor.expiresAt.plusSeconds(1)))),
        )
        for (bad in
            listOf(
                cursor.copy(page = 1),
                cursor.copy(after = UUID.randomUUID()),
                cursor.copy(upperPosition = 9),
            )) assertEquals("invalid_collaboration_sync_cursor", code(check(bad)))
        val bootstrap =
            cursor.copy(
                phase = CollaborationSummaryPhase.BOOTSTRAP,
                upperPosition = null,
                after = UUID.randomUUID(),
                page = 999,
                expiresAt = now.plusSeconds(900),
            )
        assertEquals(
            Result.Success(Unit),
            check(bootstrap, phase = CollaborationSummaryPhase.BOOTSTRAP),
        )
        assertEquals(
            "invalid_collaboration_sync_cursor",
            code(check(bootstrap.copy(page = 1000), phase = CollaborationSummaryPhase.BOOTSTRAP)),
        )
        assertEquals(
            "collaboration_summary_cursor_expired",
            code(
                check(
                    bootstrap,
                    phase = CollaborationSummaryPhase.BOOTSTRAP,
                    at = now.plusSeconds(900),
                )
            ),
        )
    }
}
