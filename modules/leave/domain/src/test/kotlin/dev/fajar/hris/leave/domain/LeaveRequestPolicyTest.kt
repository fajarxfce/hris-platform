package dev.fajar.hris.leave.domain

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.scheduledWorkDay
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveRequestPolicyTest {
    private val date = LocalDate.parse("2026-10-05")
    private val now = Instant.parse("2026-10-01T15:00:00Z")
    private val actorId = UUID.randomUUID()
    private val policy =
        LeavePolicy("Annual leave", true, true, 12, setOf(ContractKind.PERMANENT), 30)
    private val terms =
        EmploymentTerms(
            LocalDate.of(2025, 1, 1),
            ContractKind.PERMANENT,
            LocalDate.of(2025, 1, 1),
            null,
            EmploymentStatus.ACTIVE,
            null,
            null,
            null,
            null,
            null,
        )
    private val history = listOf(EmploymentRevision(0, terms, actorId, "Onboarding", now))
    private val shift =
        ShiftSnapshot(
            UUID.randomUUID(),
            0,
            ShiftDetails(
                "NIGHT",
                "Night",
                LocalTime.of(22, 0),
                LocalTime.of(6, 1),
                30,
                "Asia/Jakarta",
                WorkMode.REMOTE,
                false,
                100.0,
                null,
            ),
        )

    private fun work(day: LocalDate = date): ScheduledDay.Work =
        (scheduledWorkDay(day, shift, CalendarOrigin.PATTERN, 0) as Result.Success).value

    private fun planned(portion: LeavePortion = LeavePortion.FULL): List<LeaveDay> =
        (planLeaveDays(
                listOf(RequestedLeaveDay(date, portion)),
                EmployeeCalendar(0, listOf(work())),
                history,
                policy,
            )
                as Result.Success)
            .value

    @Test
    fun daysSkipHolidaysAndKeepTheOvernightWorkDate() {
        val result =
            planLeaveDays(
                listOf(
                    RequestedLeaveDay(date, LeavePortion.FULL),
                    RequestedLeaveDay(date.plusDays(1), LeavePortion.FULL),
                ),
                EmployeeCalendar(
                    0,
                    listOf(
                        work(),
                        ScheduledDay.Off(
                            date.plusDays(1),
                            CalendarOrigin.HOLIDAY,
                            0,
                            UUID.randomUUID(),
                        ),
                    ),
                ),
                history,
                policy,
            )
                as Result.Success
        assertEquals(1, result.value.size)
        assertEquals(date, result.value.single().workDate)
        assertEquals(Instant.parse("2026-10-05T23:01:00Z"), result.value.single().endsAt)
        assertEquals(451, result.value.single().chargedMinutes)
    }

    @Test
    fun halfDaysSplitOddMinutesExactlyAndRejectUnsupportedInput() {
        assertEquals(
            451,
            planned(LeavePortion.FIRST_HALF).single().chargedMinutes +
                planned(LeavePortion.SECOND_HALF).single().chargedMinutes,
        )
        val partial =
            planLeaveDays(
                listOf(RequestedLeaveDay(date, LeavePortion.FIRST_HALF)),
                EmployeeCalendar(0, listOf(work())),
                history,
                policy.copy(allowPartialDays = false),
            )
        assertTrue(partial is Result.Failed)
        assertTrue(
            validateRequestedLeaveDays(
                listOf(
                    RequestedLeaveDay(date, LeavePortion.FULL),
                    RequestedLeaveDay(date, LeavePortion.FIRST_HALF),
                ),
                "Leave",
            )
                is Result.Failed
        )
        assertTrue(
            validateRequestedLeaveDays(
                listOf(
                    RequestedLeaveDay(date, LeavePortion.FULL),
                    RequestedLeaveDay(date.plusDays(366), LeavePortion.FULL),
                ),
                "Leave",
            )
                is Result.Failed
        )
    }

    @Test
    fun employmentEligibilityIsCheckedAcrossFutureEffectiveChanges() {
        val ended =
            EmploymentRevision(
                1,
                terms.copy(
                    effectiveFrom = date.plusDays(1),
                    endDate = date,
                    status = EmploymentStatus.ENDED,
                ),
                actorId,
                "Separation",
                now,
            )
        val result =
            planLeaveDays(
                listOf(
                    RequestedLeaveDay(date, LeavePortion.FULL),
                    RequestedLeaveDay(date.plusDays(1), LeavePortion.FULL),
                ),
                EmployeeCalendar(0, listOf(work(), work(date.plusDays(1)))),
                history + ended,
                policy,
            )
        assertEquals("leave_employee_ineligible", (result as Result.Failed).failure.code)
        val missing =
            planLeaveDays(
                listOf(RequestedLeaveDay(date, LeavePortion.FULL)),
                EmployeeCalendar(0, listOf(ScheduledDay.Unassigned(date))),
                history,
                policy,
            )
        assertEquals("leave_schedule_missing", (missing as Result.Failed).failure.code)
    }

    @Test
    fun slotConflictsApplyAcrossTypesAndYearAllocationsRemainSeparate() {
        assertTrue(
            validateLeaveOverlap(planned(), listOf(LeaveOccupancy(date, 1))) is Result.Failed
        )
        assertTrue(
            validateLeaveOverlap(planned(LeavePortion.SECOND_HALF), listOf(LeaveOccupancy(date, 1)))
                is Result.Success
        )
        val days = planned() + planned().map { it.copy(workDate = LocalDate.of(2027, 1, 1)) }
        val reserved = leaveLedgerMovements(days, LeaveBalanceEffect.RESERVE)
        assertEquals(listOf(2026, 2027), reserved.map { it.year })
        assertTrue(reserved.all { it.availableDelta == -2 && it.reservedDelta == 2 })
        assertTrue(
            validateLeaveMovement(LeaveBalance(2026, 1, 4, 0), reserved.first()) is Result.Failed
        )
    }

    @Test
    fun onlyTerminalDecisionsMoveBalancesAndCancellationRejectionKeepsConsumption() {
        val pending =
            leaveDecisionOutcome(LeaveStatus.PENDING, ApprovalStatus.BLOCKED) as Result.Success
        assertNull(pending.value.effect)
        val approved =
            leaveDecisionOutcome(LeaveStatus.PENDING, ApprovalStatus.APPROVED) as Result.Success
        assertEquals(LeaveBalanceEffect.CONSUME, approved.value.effect)
        val denied =
            leaveDecisionOutcome(LeaveStatus.CANCELLATION_PENDING, ApprovalStatus.REJECTED)
                as Result.Success
        assertEquals(LeaveStatus.APPROVED, denied.value.status)
        assertNull(denied.value.effect)
        val cancelled =
            leaveDecisionOutcome(LeaveStatus.CANCELLATION_PENDING, ApprovalStatus.APPROVED)
                as Result.Success
        assertEquals(LeaveStatus.CANCELLED, cancelled.value.status)
        assertEquals(LeaveBalanceEffect.REFUND, cancelled.value.effect)
        assertTrue(
            leaveDecisionOutcome(LeaveStatus.APPROVED, ApprovalStatus.APPROVED) is Result.Failed
        )
    }

    @Test
    fun availableActionsUseTheSameApprovalPolicyAsCommands() {
        val owner = UUID.randomUUID()
        val approver = UUID.randomUUID()
        val approval =
            ApprovalRequest(
                UUID.randomUUID(),
                ApprovalKind.LEAVE,
                UUID.randomUUID(),
                owner,
                owner,
                UUID.randomUUID(),
                0,
                listOf(ApprovalStage(setOf(approver))),
                0,
                ApprovalStatus.PENDING,
                0,
                now,
            )
        val request =
            LeaveRequest(
                approval.resourceId,
                UUID.randomUUID(),
                "E001",
                "Example Employee",
                owner,
                owner,
                now,
                LeavePolicySnapshot(UUID.randomUUID(), "ANNUAL", 0, policy),
                planned(),
                "Leave",
                LeaveStatus.PENDING,
                approval.id,
                null,
                0,
            )
        val self =
            Actor(
                owner,
                UUID.randomUUID(),
                setOf("leave.self.manage", "leave.team.approve"),
                now,
                UUID.randomUUID(),
            )
        assertEquals(
            setOf(LeaveAction.WITHDRAW),
            leaveAvailableActions(self, request, approval, emptyList(), emptyList(), now),
        )
        val reviewer = self.copy(accountId = approver, permissions = setOf("leave.team.approve"))
        assertEquals(
            setOf(LeaveAction.DECIDE),
            leaveAvailableActions(reviewer, request, approval, emptyList(), emptyList(), now),
        )
    }
}
