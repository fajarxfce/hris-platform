package dev.fajar.hris.sync.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.domain.entities.SyncCollection.*
import dev.fajar.hris.sync.domain.policies.selectSyncCollections
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncCollectionPolicyTest {
    @Test
    fun explicitClientSelectionDoesNotAutomaticallyGrowWithServerCollections() {
        val permissions = setOf("expenses.self.manage", "payroll.self.read")
        assertEquals(
            Result.Success(setOf(EXPENSE_CLAIMS, PAYSLIPS, PAYROLL_PAYMENTS)),
            selectSyncCollections(permissions, null),
        )
        assertEquals(
            Result.Success(setOf(EXPENSE_CLAIMS, PAYSLIPS)),
            selectSyncCollections(permissions, setOf(EXPENSE_CLAIMS, PAYSLIPS)),
        )
    }

    @Test
    fun requestedCollectionsCannotElevatePermissionOrAdministrativeScope() {
        assertEquals(
            Result.Success(setOf(EXPENSE_CLAIMS)),
            selectSyncCollections(setOf("expenses.self.manage"), setOf(EXPENSE_CLAIMS, PAYSLIPS)),
        )
        for (permissions in listOf(setOf("payroll.read"), setOf("expenses.self.manage"))) {
            val failed = selectSyncCollections(permissions, setOf(PAYSLIPS)) as Result.Failed
            assertEquals("sync_access_denied", failed.failure.code)
        }
    }

    @Test
    fun emptySelectionRequiresClientCorrection() {
        val failed =
            selectSyncCollections(setOf("expenses.self.manage"), emptySet()) as Result.Failed
        assertEquals("invalid_sync_collections", failed.failure.code)
        assertEquals(mapOf("collections" to "required"), failed.failure.fields)
    }

    @Test
    fun inboxRequiresAnExplicitSelectionAndItsOwnReadPermission() {
        val permissions = setOf("expenses.self.manage", "announcements.read")
        assertEquals(
            Result.Success(setOf(EXPENSE_CLAIMS)),
            selectSyncCollections(permissions, null),
        )
        assertEquals(Result.Success(setOf(INBOX)), selectSyncCollections(permissions, setOf(INBOX)))
        assertEquals(
            Result.Success(setOf(EXPENSE_CLAIMS, INBOX)),
            selectSyncCollections(permissions, setOf(EXPENSE_CLAIMS, INBOX)),
        )
        val unselected = selectSyncCollections(setOf("announcements.read"), null) as Result.Failed
        assertEquals("sync_access_denied", unselected.failure.code)
        val unprivileged =
            selectSyncCollections(setOf("announcements.manage"), setOf(INBOX)) as Result.Failed
        assertEquals("sync_access_denied", unprivileged.failure.code)
    }
}
