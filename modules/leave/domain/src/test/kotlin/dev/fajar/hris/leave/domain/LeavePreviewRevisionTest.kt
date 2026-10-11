package dev.fajar.hris.leave.domain

import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.leavePreviewRevision
import dev.fajar.hris.workforce.domain.entities.CalendarOrigin
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeavePreviewRevisionTest {
    private val company = UUID.randomUUID()
    private val employee = UUID.randomUUID()
    private val policy =
        LeavePolicySnapshot(
            UUID.randomUUID(),
            "ANNUAL",
            3,
            LeavePolicy("Annual leave", true, true, 0, emptySet(), 30),
        )
    private val days =
        listOf(
            LeaveDay(
                LocalDate.parse("2026-10-05"),
                LeavePortion.FIRST_HALF,
                Instant.parse("2026-10-05T01:00:00Z"),
                Instant.parse("2026-10-05T10:00:00Z"),
                480,
                240,
                UUID.randomUUID(),
                2,
                CalendarOrigin.PATTERN,
                4,
            )
        )
    private val selected =
        listOf(
            RequestedLeaveDay(days.single().workDate, LeavePortion.FIRST_HALF),
            RequestedLeaveDay(LocalDate.parse("2026-10-10"), LeavePortion.FULL),
        )
    private val balance =
        LeavePreviewBalance(LeaveBalance(2026, 20, 1, 2, 6, false, UUID.randomUUID()), 1)

    @Test
    fun inputOrderDoesNotChangeTheRevisionButIdentityAndExcludedDatesDo() {
        val expected = revision()
        assertTrue(expected.matches(Regex("[0-9a-f]{64}")))
        assertEquals(expected, revision(selected = selected.reversed()))
        assertNotEquals(expected, revision(companyId = UUID.randomUUID()))
        assertNotEquals(expected, revision(employeeId = UUID.randomUUID()))
        assertNotEquals(expected, revision(selected = selected.take(1)))
        assertNotEquals(expected, revision(timezone = "Asia/Makassar"))
    }

    @Test
    fun changedPolicyCalendarAndLedgerFactsRequireAnotherReview() {
        val expected = revision()
        assertNotEquals(expected, revision(policy = policy.copy(revision = 4)))
        for (day in
            listOf(
                days.single().copy(shiftRevision = 3),
                days.single().copy(scheduleRevision = 5),
                days.single().copy(startsAt = days.single().startsAt.plusSeconds(60)),
                days.single().copy(chargedMinutes = 241),
            )) {
            assertNotEquals(expected, revision(days = listOf(day)))
        }
        for (ledger in
            listOf(
                balance.balance.copy(version = 7),
                balance.balance.copy(availableHalfDays = 21),
                balance.balance.copy(reservedHalfDays = 2),
                balance.balance.copy(closed = true),
                balance.balance.copy(accountId = UUID.randomUUID()),
            )) {
            assertNotEquals(expected, revision(balances = listOf(balance.copy(balance = ledger))))
        }
    }

    private fun revision(
        companyId: UUID = company,
        employeeId: UUID = employee,
        timezone: String = "Asia/Jakarta",
        policy: LeavePolicySnapshot = this.policy,
        selected: List<RequestedLeaveDay> = this.selected,
        days: List<LeaveDay> = this.days,
        balances: List<LeavePreviewBalance> = listOf(balance),
    ) = leavePreviewRevision(companyId, employeeId, timezone, policy, selected, days, balances)
}
