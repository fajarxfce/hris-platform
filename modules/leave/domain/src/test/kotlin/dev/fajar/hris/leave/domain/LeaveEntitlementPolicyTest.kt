package dev.fajar.hris.leave.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.people.domain.entities.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveEntitlementPolicyTest {
    private val september = YearMonth.of(2026, 9)
    private val october = LocalDate.of(2026, 10, 1)
    private val employment =
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
    private val policy =
        LeavePolicy(
            "Annual leave",
            true,
            true,
            12,
            setOf(ContractKind.PERMANENT),
            30,
            accrual = LeaveAccrualPolicy(LeaveAccrualFrequency.MONTHLY, 2, 4),
        )

    private fun revision(terms: EmploymentTerms = employment, n: Long = 0) =
        EmploymentRevision(n, terms, UUID.randomUUID(), "Employment", Instant.EPOCH)

    private fun code(value: Result<*>): String {
        assertTrue(value is Result.Failed, value.toString())
        return (value as Result.Failed).failure.code
    }

    @Test
    fun monthlyAccrualUsesCompletedWholeCalendarMonthsAndDatedEligibility() {
        val result =
            calculateLeaveAccrual(september, october, listOf(revision()), policy) as Result.Success
        assertEquals(
            LeaveAccrualAward(september, september.atDay(1), september.atEndOfMonth(), 2),
            result.value,
        )
        assertEquals(
            "leave_accrual_not_due",
            code(
                calculateLeaveAccrual(
                    september,
                    september.atEndOfMonth(),
                    listOf(revision()),
                    policy,
                )
            ),
        )
        val lateJoin =
            employment.copy(
                effectiveFrom = LocalDate.of(2025, 9, 15),
                startDate = LocalDate.of(2025, 9, 15),
            )
        assertEquals(
            "leave_employee_ineligible",
            code(calculateLeaveAccrual(september, october, listOf(revision(lateJoin)), policy)),
        )
        val suspended =
            employment.copy(
                effectiveFrom = september.atDay(15),
                status = EmploymentStatus.SUSPENDED,
            )
        assertEquals(
            "leave_employee_ineligible",
            code(
                calculateLeaveAccrual(
                    september,
                    october,
                    listOf(revision(), revision(suspended, 1)),
                    policy,
                )
            ),
        )
    }

    @Test
    fun annualAwardsHaveOneCalendarYearKeyAndExplicitQualifyingDate() {
        val annual = policy.copy(accrual = LeaveAccrualPolicy(LeaveAccrualFrequency.ANNUAL, 24, 4))
        val terms =
            employment.copy(
                effectiveFrom = LocalDate.of(2025, 9, 15),
                startDate = LocalDate.of(2025, 9, 15),
            )
        val result =
            calculateLeaveAccrual(
                september,
                LocalDate.of(2026, 9, 16),
                listOf(revision(terms)),
                annual,
            )
                as Result.Success
        assertEquals(YearMonth.of(2026, 1), result.value.period)
        assertEquals(LocalDate.of(2026, 9, 15), result.value.eligibleFrom)
        assertEquals(result.value.eligibleFrom, result.value.eligibleUntil)
        assertEquals(
            result.value.period,
            leaveAccrualPeriod(YearMonth.of(2026, 12), LeaveAccrualFrequency.ANNUAL),
        )
        assertEquals(
            "leave_employee_ineligible",
            code(
                calculateLeaveAccrual(
                    september,
                    LocalDate.of(2026, 9, 14),
                    listOf(revision(terms)),
                    annual,
                )
            ),
        )
    }

    @Test
    fun policyBoundsDoNotInventGrantsOrRoundHalfDaysForWholeDayPolicies() {
        assertTrue(
            validateLeaveAccrualPolicy(
                LeaveAccrualPolicy(LeaveAccrualFrequency.MANUAL, 0, 0),
                false,
            )
                is Result.Success
        )
        assertTrue(
            validateLeaveAccrualPolicy(
                LeaveAccrualPolicy(LeaveAccrualFrequency.MONTHLY, 1, 0),
                false,
            )
                is Result.Failed
        )
        assertTrue(
            validateLeaveAccrualPolicy(
                LeaveAccrualPolicy(LeaveAccrualFrequency.MONTHLY, 63, 0),
                true,
            )
                is Result.Failed
        )
        assertEquals(
            "leave_accrual_not_configured",
            code(
                calculateLeaveAccrual(
                    september,
                    october,
                    listOf(revision()),
                    policy.copy(accrual = null),
                )
            ),
        )
        assertEquals(
            "leave_accrual_not_configured",
            code(
                calculateLeaveAccrual(
                    september,
                    october,
                    listOf(revision()),
                    policy.copy(accrual = LeaveAccrualPolicy(LeaveAccrualFrequency.MANUAL, 0, 4)),
                )
            ),
        )
    }

    @Test
    fun yearClosingRequiresResolvedReservationsAndAnOpenDestinationWhenCarrying() {
        val balance = LeaveBalance(2026, 10, 0, 3)
        val next = LeaveBalance(2027, 0, 0, 0)
        assertEquals(
            LeaveYearRollover(4, 6),
            (calculateLeaveYearRollover(balance, 4, next, false) as Result.Success).value,
        )
        assertEquals(
            "leave_resolution_required",
            code(calculateLeaveYearRollover(balance, 4, next, true)),
        )
        assertEquals(
            "leave_resolution_required",
            code(calculateLeaveYearRollover(balance.copy(reservedHalfDays = 1), 4, next, false)),
        )
        assertEquals(
            "leave_destination_year_closed",
            code(calculateLeaveYearRollover(balance, 4, next.copy(closed = true), false)),
        )
        assertTrue(
            calculateLeaveYearRollover(balance, 0, next.copy(closed = true), false)
                is Result.Success
        )
        assertEquals(
            "leave_year_closed",
            code(validateLeaveBalanceAdjustment(balance.copy(closed = true), 1)),
        )
        assertEquals(
            "leave_balance_limit",
            code(
                calculateLeaveYearRollover(
                    balance,
                    4,
                    next.copy(availableHalfDays = Int.MAX_VALUE),
                    false,
                )
            ),
        )
    }

    @Test
    fun boundedEmploymentHistoryAndCancellationTerminateWithoutPartialResults() {
        assertEquals(
            "employment_history_capacity",
            code(calculateLeaveAccrual(september, october, List(1001) { revision() }, policy)),
        )
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                calculateLeaveAccrual(september, october, listOf(revision()), policy)
            }
        } finally {
            Thread.interrupted()
        }
        assertTrue(
            calculateLeaveAccrual(september, october, listOf(revision()), policy) is Result.Success
        )
    }
}
