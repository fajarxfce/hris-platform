package dev.fajar.hris.leave.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.workforce.domain.entities.CalendarOrigin
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveCarryFundingPolicyTest {
    private val before = Instant.parse("2027-03-01T00:00:00Z")
    private val expiry = Instant.parse("2027-03-31T17:00:00Z")

    private fun bucket(
        available: Int = 6,
        reserved: Int = 0,
        consumed: Int = 0,
        year: Int = 2027,
    ): LeaveCarryBucket =
        LeaveCarryBucket(
            UUID(0, year.toLong()),
            year,
            available + reserved + consumed,
            available,
            reserved,
            consumed,
            validity = (leaveCarryValidity(year, 3, "Asia/Jakarta") as Result.Success).value,
        )

    private fun day(value: String, portion: LeavePortion = LeavePortion.FULL): LeaveDay {
        val date = LocalDate.parse(value)
        val start = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        return LeaveDay(
            date,
            portion,
            start,
            start.plusSeconds(8 * 3600),
            480,
            portion.halfDays * 240L,
            UUID(0, 1),
            0,
            CalendarOrigin.PATTERN,
            0,
        )
    }

    private fun apply(
        bucket: LeaveCarryBucket,
        changes: List<LeaveCarryMovement>,
    ): LeaveCarryBucket =
        changes.fold(bucket) { current, change ->
            (applyLeaveCarryMovement(current, change) as Result.Success).value
        }

    private fun balance(bucket: LeaveCarryBucket, ordinary: Int = 0) =
        LeaveBalance(
            bucket.year,
            bucket.availableHalfDays + ordinary,
            bucket.reservedHalfDays,
            bucket.consumedHalfDays,
        )

    @Test
    fun expiryConfigurationIsOptionalAndRequiresPositiveCarriedCredit() {
        val policy = LeaveAccrualPolicy(LeaveAccrualFrequency.ANNUAL, 24, 6)
        assertEquals(Result.Success(Unit), validateLeaveAccrualPolicy(policy, true))
        for (months in 1..12) assertEquals(
            Result.Success(Unit),
            validateLeaveAccrualPolicy(policy.copy(carryValidityMonths = months), true),
        )
        for (months in listOf(0, 13, Int.MAX_VALUE)) assertTrue(
            validateLeaveAccrualPolicy(policy.copy(carryValidityMonths = months), true)
                is Result.Failed
        )
        assertTrue(
            validateLeaveAccrualPolicy(
                policy.copy(carryLimitHalfDays = 0, carryValidityMonths = 3),
                true,
            )
                is Result.Failed
        )
    }

    @Test
    fun absentExpiryDoesNotInventADeadlineOrChangeExistingCredit() {
        assertEquals(
            Result.Success<LeaveCarryValidity?>(null),
            leaveCarryValidity(2027, null, "Asia/Jakarta"),
        )
        val unlimited = bucket().copy(validity = null)
        assertEquals(
            Result.Success<LeaveCarryMovement?>(null),
            planLeaveCarryExpiry(unlimited, Instant.parse("2200-01-01T00:00:00Z")),
        )
        val plan =
            planLeaveCarryReservation(
                listOf(day("2027-12-31")),
                listOf(balance(unlimited)),
                listOf(unlimited),
                before,
            )
        assertEquals(Result.Success(listOf(LeaveCarryAllocation(unlimited.id, 2027, 2))), plan)
    }

    @Test
    fun validityUsesCompleteCalendarMonthsAndCompanyMidnight() {
        assertEquals(
            Result.Success(
                LeaveCarryValidity(LocalDate.of(2028, 2, 29), Instant.parse("2028-02-29T17:00:00Z"))
            ),
            leaveCarryValidity(2028, 2, "Asia/Jakarta"),
        )
        assertEquals(
            Result.Success(
                LeaveCarryValidity(
                    LocalDate.of(2027, 12, 31),
                    Instant.parse("2027-12-31T10:00:00Z"),
                )
            ),
            leaveCarryValidity(2027, 12, "Pacific/Kiritimati"),
        )
        assertEquals(
            Instant.parse("2026-11-01T05:00:00Z"),
            (leaveCarryValidity(2026, 10, "America/Havana") as Result.Success).value?.expiresAt,
        )
    }

    @Test
    fun invalidPolicyCannotWrapYearsOrCreateAnUnboundedValidity() {
        for (months in listOf(Int.MIN_VALUE, 0, 13, Int.MAX_VALUE)) assertTrue(
            leaveCarryValidity(2027, months, "Asia/Jakarta") is Result.Failed
        )
        for (year in listOf(1900, 2201)) assertTrue(
            leaveCarryValidity(year, 3, "Asia/Jakarta") is Result.Failed
        )
        assertTrue(leaveCarryValidity(2027, 3, "invalid/zone") is Result.Failed)
    }

    @Test
    fun eligibleCarryIsReservedBeforeOrdinaryCreditWithHalfDayPrecision() {
        val carry = bucket(available = 3)
        val plan =
            planLeaveCarryReservation(
                listOf(day("2027-03-20"), day("2027-03-21")),
                listOf(balance(carry, ordinary = 1)),
                listOf(carry),
                before,
            )
        assertEquals(Result.Success(listOf(LeaveCarryAllocation(carry.id, 2027, 3))), plan)
        assertEquals(3, carry.availableHalfDays)
        val half =
            planLeaveCarryReservation(
                listOf(day("2027-03-20", LeavePortion.FIRST_HALF)),
                listOf(balance(carry)),
                listOf(carry),
                before,
            )
        assertEquals(Result.Success(listOf(LeaveCarryAllocation(carry.id, 2027, 1))), half)
    }

    @Test
    fun futureLeaveCannotSpendCreditThatExpiresBeforeItsWorkDate() {
        val carry = bucket(available = 3)
        assertEquals(
            Result.Success(emptyList<LeaveCarryAllocation>()),
            planLeaveCarryReservation(
                listOf(day("2027-04-01")),
                listOf(balance(carry, ordinary = 2)),
                listOf(carry),
                before,
            ),
        )
        val insufficient =
            planLeaveCarryReservation(
                listOf(day("2027-04-01")),
                listOf(balance(carry, ordinary = 1)),
                listOf(carry),
                before,
            )
        assertEquals("insufficient_leave_balance", (insufficient as Result.Failed).failure.code)
    }

    @Test
    fun bookingAtTheDeadlineCannotSpendExpiredCreditEvenForAnEarlierWorkDate() {
        val carry = bucket()
        assertTrue(
            planLeaveCarryReservation(
                listOf(day("2027-03-31")),
                listOf(balance(carry)),
                listOf(carry),
                expiry.minusNanos(1),
            )
                is Result.Success
        )
        assertTrue(
            planLeaveCarryReservation(
                listOf(day("2027-03-31")),
                listOf(balance(carry)),
                listOf(carry),
                expiry,
            )
                is Result.Failed
        )
        assertEquals(6, (planLeaveCarryExpiry(carry, expiry) as Result.Success).value?.halfDays)
        assertNull((planLeaveCarryExpiry(carry, expiry.minusNanos(1)) as Result.Success).value)
    }

    @Test
    fun mixedWorkDatesReserveOnlyTheEligiblePortionAndDoNotBorrowAcrossYears() {
        val carry = bucket(available = 6)
        val days = listOf(day("2027-04-01"), day("2027-03-31"))
        val expected = Result.Success(listOf(LeaveCarryAllocation(carry.id, 2027, 2)))
        assertEquals(
            expected,
            planLeaveCarryReservation(
                days,
                listOf(balance(carry, ordinary = 2)),
                listOf(carry),
                before,
            ),
        )
        assertEquals(
            expected,
            planLeaveCarryReservation(
                days.reversed(),
                listOf(balance(carry, ordinary = 2)),
                listOf(carry),
                before,
            ),
        )
        val following = bucket(year = 2028)
        val across =
            planLeaveCarryReservation(
                listOf(day("2027-12-31"), day("2028-01-01")),
                listOf(balance(carry), balance(following)),
                listOf(carry, following),
                before,
            )
        assertEquals("insufficient_leave_balance", (across as Result.Failed).failure.code)
    }

    @Test
    fun twoYearRequestRetainsDistinctCarryAllocations() {
        val first = bucket().copy(validity = null)
        val next = bucket(year = 2028)
        val result =
            planLeaveCarryReservation(
                listOf(day("2028-01-01"), day("2027-12-31", LeavePortion.SECOND_HALF)),
                listOf(balance(first), balance(next)),
                listOf(next, first),
                before,
            )
        assertEquals(
            Result.Success(
                listOf(
                    LeaveCarryAllocation(first.id, 2027, 1),
                    LeaveCarryAllocation(next.id, 2028, 2),
                )
            ),
            result,
        )
        val owned = (result as Result.Success).value
        assertThrows(UnsupportedOperationException::class.java) {
            (owned as MutableList<LeaveCarryAllocation>).clear()
        }
    }

    @Test
    fun corruptOrAmbiguousFundingSnapshotsCannotCreateOrdinaryCredit() {
        val carry = bucket()
        val days = listOf(day("2027-03-20"))
        assertTrue(
            planLeaveCarryReservation(
                days,
                listOf(LeaveBalance(2027, 5, 0, 0)),
                listOf(carry),
                before,
            )
                is Result.Failed
        )
        assertTrue(
            planLeaveCarryReservation(
                days,
                listOf(balance(carry)),
                listOf(carry, carry.copy(id = UUID(0, 99))),
                before,
            )
                is Result.Failed
        )
        assertTrue(
            planLeaveCarryReservation(
                days,
                listOf(balance(carry), balance(carry)),
                listOf(carry),
                before,
            )
                is Result.Failed
        )
        assertTrue(
            planLeaveCarryReservation(
                days,
                listOf(balance(carry)),
                listOf(carry.copy(expiredHalfDays = 1)),
                before,
            )
                is Result.Failed
        )
        assertTrue(
            planLeaveCarryReservation(
                days,
                listOf(balance(carry).copy(closed = true)),
                listOf(carry),
                before,
            )
                is Result.Failed
        )
        assertTrue(
            planLeaveCarryReservation(
                days,
                listOf(balance(carry).copy(availableHalfDays = -1)),
                listOf(carry),
                before,
            )
                is Result.Failed
        )
    }

    @Test
    fun aPriorReservationCanBeConsumedAfterTheDeadlineWithoutExpiringReservedUnits() {
        val carry = bucket(available = 4, reserved = 2)
        val plan =
            (planLeaveCarryCompletion(carry, LeaveBalanceEffect.CONSUME, 2, expiry)
                    as Result.Success)
                .value
        assertEquals(
            listOf(LeaveCarryEffect.CONSUME, LeaveCarryEffect.EXPIRE),
            plan.map { it.effect },
        )
        val after = apply(carry, plan)
        assertEquals(0, after.availableHalfDays)
        assertEquals(0, after.reservedHalfDays)
        assertEquals(2, after.consumedHalfDays)
        assertEquals(4, after.expiredHalfDays)
    }

    @Test
    fun expiryTouchesOnlyFreeUnitsAndPreservesTheReservedEntitlement() {
        val carry = bucket(available = 4, reserved = 2)
        val expiryPlan =
            requireNotNull((planLeaveCarryExpiry(carry, expiry) as Result.Success).value)
        val after = apply(carry, listOf(expiryPlan))
        assertEquals(2, after.reservedHalfDays)
        assertEquals(4, after.expiredHalfDays)
        assertNull((planLeaveCarryExpiry(after, expiry) as Result.Success).value)
        val completion =
            (planLeaveCarryCompletion(after, LeaveBalanceEffect.CONSUME, 2, expiry)
                    as Result.Success)
                .value
        assertEquals(1, completion.size)
        assertEquals(2, apply(after, completion).consumedHalfDays)
    }

    @Test
    fun withdrawalBeforeTheDeadlineRestoresTheSameCarrySource() {
        val carry = bucket(available = 4, reserved = 2)
        val plan =
            (planLeaveCarryCompletion(carry, LeaveBalanceEffect.RELEASE, 2, before)
                    as Result.Success)
                .value
        assertEquals(listOf(LeaveCarryEffect.RELEASE), plan.map { it.effect })
        val after = apply(carry, plan)
        assertEquals(6, after.availableHalfDays)
        assertEquals(0, after.expiredHalfDays)
    }

    @Test
    fun withdrawalAndCancellationAfterTheDeadlineNeverMintFreshAvailableCredit() {
        for (effect in listOf(LeaveBalanceEffect.RELEASE, LeaveBalanceEffect.REFUND)) {
            val carry =
                if (effect == LeaveBalanceEffect.RELEASE) bucket(available = 2, reserved = 4)
                else bucket(available = 2, consumed = 4)
            val first = (planLeaveCarryCompletion(carry, effect, 2, expiry) as Result.Success).value
            val after = apply(carry, first)
            assertEquals(0, after.availableHalfDays)
            assertEquals(4, after.expiredHalfDays)
            val second =
                (planLeaveCarryCompletion(after, effect, 2, expiry) as Result.Success).value
            val complete = apply(after, second)
            assertEquals(6, complete.expiredHalfDays)
            assertEquals(0, complete.availableHalfDays)
            assertTrue(planLeaveCarryCompletion(complete, effect, 1, expiry) is Result.Failed)
        }
    }

    @Test
    fun ordinaryDebitsAndYearTransferKeepDistinctCarryAccounting() {
        val original = bucket()
        val after =
            apply(
                original,
                listOf(
                    LeaveCarryMovement(original.id, LeaveCarryEffect.DEDUCT, 1),
                    LeaveCarryMovement(original.id, LeaveCarryEffect.TRANSFER, 2),
                    LeaveCarryMovement(original.id, LeaveCarryEffect.EXPIRE, 3),
                ),
            )
        assertEquals(1, after.deductedHalfDays)
        assertEquals(2, after.transferredHalfDays)
        assertEquals(3, after.expiredHalfDays)
        assertEquals(0, after.availableHalfDays)
        assertEquals(3L, after.version)
    }

    @Test
    fun invalidMovementsCannotBorrowAnotherBucketOrOverflowItsVersion() {
        val carry = bucket()
        assertTrue(
            applyLeaveCarryMovement(
                carry,
                LeaveCarryMovement(UUID(0, 99), LeaveCarryEffect.RESERVE, 1),
            )
                is Result.Failed
        )
        for (units in listOf(Int.MIN_VALUE, 0, 7, Int.MAX_VALUE)) assertTrue(
            applyLeaveCarryMovement(
                carry,
                LeaveCarryMovement(carry.id, LeaveCarryEffect.RESERVE, units),
            )
                is Result.Failed
        )
        assertTrue(
            applyLeaveCarryMovement(
                carry.copy(version = Long.MAX_VALUE),
                LeaveCarryMovement(carry.id, LeaveCarryEffect.RESERVE, 1),
            )
                is Result.Failed
        )
        assertTrue(
            planLeaveCarryCompletion(carry, LeaveBalanceEffect.RESERVE, 1, before) is Result.Failed
        )
    }

    @Test
    fun expiryAndLateRefundConserveEveryUnitAtEverySupportedCarrySize() {
        for (units in 1..732) {
            val original = bucket(available = 0, reserved = units)
            val used =
                apply(
                    original,
                    (planLeaveCarryCompletion(original, LeaveBalanceEffect.CONSUME, units, expiry)
                            as Result.Success)
                        .value,
                )
            val returned =
                apply(
                    used,
                    (planLeaveCarryCompletion(used, LeaveBalanceEffect.REFUND, units, expiry)
                            as Result.Success)
                        .value,
                )
            assertEquals(units, returned.expiredHalfDays)
            assertEquals(
                0,
                returned.availableHalfDays + returned.reservedHalfDays + returned.consumedHalfDays,
            )
            assertEquals(Result.Success(Unit), validateLeaveCarryBucket(returned))
        }
    }
}
