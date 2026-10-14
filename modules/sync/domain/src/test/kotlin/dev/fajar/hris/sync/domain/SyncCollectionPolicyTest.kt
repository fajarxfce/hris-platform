package dev.fajar.hris.sync.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.domain.entities.SyncCollection.*
import dev.fajar.hris.sync.domain.policies.selectSyncCollections
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncCollectionPolicyTest {
    @Test
    fun payrollReadersSelectOwnedResourcesWithoutChangingTheDefaultProjection() {
        val collections = setOf(PAYSLIPS, PAYROLL_PAYMENTS)
        for (grant in listOf("payroll.self.read", "payroll.read")) {
            assertEquals(
                Result.Success(collections),
                selectSyncCollections(setOf(grant), collections),
            )
        }
        val permissions = setOf("payroll.read", "expenses.self.manage")
        assertEquals(
            Result.Success(setOf(EXPENSE_CLAIMS)),
            selectSyncCollections(permissions, null),
        )
        assertEquals(
            Result.Success(collections + EXPENSE_CLAIMS),
            selectSyncCollections(permissions, collections + EXPENSE_CLAIMS),
        )
    }

    @Test
    fun calendarSourcesAreExplicitAndNeverGrantedByTeamOrCompanyMetadataAccess() {
        val sources = setOf(WORK_SCHEDULES, ROSTER_DAYS, WORK_HOLIDAYS)
        for (grant in listOf("attendance.self.record", "workforce.read")) {
            assertEquals(Result.Success(sources), selectSyncCollections(setOf(grant), sources))
            assertEquals(
                "sync_access_denied",
                (selectSyncCollections(setOf(grant), null) as Result.Failed).failure.code,
            )
        }
        for (grant in
            listOf(
                "company.read",
                "workforce.team.read",
                "attendance.verify",
                "overtime.approve",
            )) {
            assertEquals(
                "sync_access_denied",
                (selectSyncCollections(setOf(grant), sources) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun attendanceRequiresExplicitSelectionAndAnOwnedReadGrant() {
        for (grant in listOf("attendance.self.record", "workforce.read")) {
            assertEquals(
                Result.Success(setOf(ATTENDANCE_DAYS)),
                selectSyncCollections(setOf(grant), setOf(ATTENDANCE_DAYS)),
            )
            assertEquals(
                "sync_access_denied",
                (selectSyncCollections(setOf(grant), null) as Result.Failed).failure.code,
            )
        }
        for (grant in
            listOf("workforce.team.read", "attendance.verify", "attendance.team.verify")) {
            assertEquals(
                "sync_access_denied",
                (selectSyncCollections(setOf(grant), setOf(ATTENDANCE_DAYS)) as Result.Failed)
                    .failure
                    .code,
            )
        }
        assertEquals(
            Result.Success(setOf(LEAVE_REQUESTS, LEAVE_BALANCES)),
            selectSyncCollections(setOf("attendance.self.record", "leave.self.manage"), null),
        )
    }

    @Test
    fun overtimeReadGrantsRequireExplicitSelectionAndKeepTheOwnedProjection() {
        for (grant in listOf("overtime.read", "overtime.manage", "workforce.read")) {
            val permissions = setOf(grant, "payroll.read")
            assertEquals(
                "sync_access_denied",
                (selectSyncCollections(permissions, null) as Result.Failed).failure.code,
            )
            assertEquals(
                Result.Success(setOf(OVERTIME_REQUESTS, PAYSLIPS)),
                selectSyncCollections(permissions, setOf(OVERTIME_REQUESTS, PAYSLIPS)),
            )
        }
        assertEquals(
            "sync_access_denied",
            (selectSyncCollections(setOf("overtime.approve"), setOf(OVERTIME_REQUESTS))
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun readOnlyLeaveAndPayrollRequireExplicitSelection() {
        val permissions = setOf("leave.read", "payroll.read")
        assertEquals(
            "sync_access_denied",
            (selectSyncCollections(permissions, null) as Result.Failed).failure.code,
        )
        assertEquals(
            Result.Success(setOf(LEAVE_REQUESTS, LEAVE_BALANCES, PAYSLIPS)),
            selectSyncCollections(permissions, setOf(LEAVE_REQUESTS, LEAVE_BALANCES, PAYSLIPS)),
        )
        assertEquals(
            Result.Success(setOf(LEAVE_REQUESTS)),
            selectSyncCollections(permissions, setOf(LEAVE_REQUESTS)),
        )
        assertEquals(
            "sync_access_denied",
            (selectSyncCollections(setOf("leave.team.read"), setOf(LEAVE_REQUESTS))
                    as Result.Failed)
                .failure
                .code,
        )
    }

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
        for (permissions in
            listOf(setOf("payroll.pay"), setOf("payroll.approve"), setOf("expenses.self.manage"))) {
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
