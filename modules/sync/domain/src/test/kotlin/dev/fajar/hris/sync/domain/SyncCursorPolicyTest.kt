package dev.fajar.hris.sync.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.domain.entities.*
import dev.fajar.hris.sync.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncCursorPolicyTest {
    private val at = Instant.parse("2026-10-09T00:00:00Z")
    private val scope =
        SyncScope(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            2,
            3,
            setOf("expenses.self.manage"),
            setOf(UUID.randomUUID()),
            setOf(SyncCollection.EXPENSE_CLAIMS),
        )
    private val head = SyncHead(UUID.randomUUID(), 30, 10, at)
    private val cursor =
        SyncCursor(
            scope.accountId,
            scope.companyId,
            head.epoch,
            "a".repeat(64),
            SyncCursorPhase.CHANGES,
            20,
            at,
            at.plusSeconds(604800),
        )

    private fun check(value: SyncCursor) =
        validateSyncCursor(value, scope, cursor.scopeFingerprint, head, SyncCursorPhase.CHANGES, at)

    private fun code(result: Result<*>) = (result as Result.Failed).failure.code

    @Test
    fun employeeCollectionsDoNotExpandIntoAdministrativeDirectories() {
        assertTrue(
            selfSyncCollections(setOf("expenses.read", "leave.read", "people.read")).isEmpty()
        )
        assertEquals(
            setOf(
                SyncCollection.EXPENSE_CLAIMS,
                SyncCollection.LEAVE_REQUESTS,
                SyncCollection.LEAVE_BALANCES,
            ),
            selfSyncCollections(setOf("expenses.self.manage", "leave.self.manage")),
        )
    }

    @Test
    fun identityModeAndScopeChangesCannotReuseACursor() {
        assertTrue(check(cursor) is Result.Success)
        assertEquals("invalid_sync_cursor", code(check(cursor.copy(accountId = UUID.randomUUID()))))
        assertEquals("invalid_sync_cursor", code(check(cursor.copy(companyId = UUID.randomUUID()))))
        assertEquals(
            "invalid_sync_cursor",
            code(check(cursor.copy(phase = SyncCursorPhase.BOOTSTRAP))),
        )
        assertEquals("sync_scope_changed", code(check(cursor.copy(epoch = UUID.randomUUID()))))
        assertEquals(
            "sync_scope_changed",
            code(check(cursor.copy(scopeFingerprint = "b".repeat(64)))),
        )
    }

    @Test
    fun expirationFutureClocksAndExcessiveLifetimesAreRejected() {
        assertEquals("sync_cursor_expired", code(validateSyncCursorTime(cursor, cursor.expiresAt)))
        assertEquals("invalid_sync_cursor", code(check(cursor.copy(issuedAt = at.plusSeconds(31)))))
        assertEquals(
            "invalid_sync_cursor",
            code(check(cursor.copy(expiresAt = at.plusSeconds(604801)))),
        )
        val snapshot =
            cursor.copy(phase = SyncCursorPhase.BOOTSTRAP, expiresAt = at.plusSeconds(900))
        assertTrue(validateSyncCursorTime(snapshot, at.plusSeconds(899)) is Result.Success)
        assertEquals(
            "sync_cursor_expired",
            code(validateSyncCursorTime(snapshot, at.plusSeconds(900))),
        )
    }

    @Test
    fun retentionAndFuturePositionsForceAResetInsteadOfSkippingData() {
        for (value in
            listOf(
                cursor.copy(position = 9),
                cursor.copy(position = 31),
                cursor.copy(upperPosition = 31),
            )) {
            assertEquals("sync_cursor_out_of_range", code(check(value)))
        }
        assertTrue(check(cursor.copy(position = 10)) is Result.Success)
        assertEquals("invalid_sync_cursor", code(check(cursor.copy(upperPosition = 19))))
    }

    @Test
    fun paginationStateCannotEscapeItsModeOrBound() {
        assertEquals("invalid_sync_cursor", code(check(cursor.copy(page = 1))))
        assertEquals(
            "invalid_sync_cursor",
            code(
                check(
                    cursor.copy(
                        after = SyncResourceKey(SyncCollection.EXPENSE_CLAIMS, UUID.randomUUID())
                    )
                )
            ),
        )
        val snapshot =
            cursor.copy(
                phase = SyncCursorPhase.BOOTSTRAP,
                expiresAt = at.plusSeconds(900),
                page = 1000,
            )
        assertEquals(
            "invalid_sync_cursor",
            code(
                validateSyncCursor(
                    snapshot,
                    scope,
                    cursor.scopeFingerprint,
                    head,
                    SyncCursorPhase.BOOTSTRAP,
                    at,
                )
            ),
        )
    }
}
