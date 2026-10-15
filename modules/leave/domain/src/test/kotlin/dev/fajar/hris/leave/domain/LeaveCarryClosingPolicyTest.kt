package dev.fajar.hris.leave.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveCarryClosingPolicyTest {
    private val january = Instant.parse("2028-01-05T03:00:00Z")
    private val oldValidity =
        LeaveCarryValidity(LocalDate.of(2027, 3, 31), Instant.parse("2027-03-31T17:00:00Z"))
    private val bucket = LeaveCarryBucket(UUID(0, 1), 2027, 6, 6, validity = oldValidity)
    private val balance = LeaveBalance(2027, 10, 0, 0, version = 7)
    private val next = LeaveBalance(2028, 0, 0, 0)
    private val policy = LeaveAccrualPolicy(LeaveAccrualFrequency.MANUAL, 0, 4, 3)

    private fun plan(
        credit: LeaveCarryBucket? = bucket,
        account: LeaveBalance = balance,
        at: Instant = january,
        destination: LeaveBalance = next,
        pending: Boolean = false,
        terms: LeaveAccrualPolicy? = policy,
    ) = planLeaveYearClosing(account, credit, terms, destination, pending, at, "Asia/Jakarta")

    @Test
    fun oldExpiredCreditCannotBecomeANewCarryAward() {
        val result = (plan() as Result.Success).value
        assertEquals(4, result.source.availableHalfDays)
        assertEquals(8, result.source.version)
        assertEquals(LeaveYearRollover(4, 0), result.rollover)
        assertEquals(LeaveCarryMovement(bucket.id, LeaveCarryEffect.EXPIRE, 6), result.settlement)
        assertTrue(result.movements.isEmpty())
        assertEquals(Instant.parse("2028-03-31T17:00:00Z"), result.validity!!.expiresAt)
    }

    @Test
    fun unexpiredCarryTransfersFirstAndExcessRemainsAnExplicitExpiry() {
        val result = (plan(bucket.copy(validity = null)) as Result.Success).value
        assertNull(result.settlement)
        assertEquals(balance, result.source)
        assertEquals(LeaveYearRollover(4, 6), result.rollover)
        assertEquals(
            listOf(
                LeaveCarryMovement(bucket.id, LeaveCarryEffect.TRANSFER, 4),
                LeaveCarryMovement(bucket.id, LeaveCarryEffect.EXPIRE, 2),
            ),
            result.movements,
        )
        assertThrows(UnsupportedOperationException::class.java) {
            (result.movements as MutableList).clear()
        }
    }

    @Test
    fun closingAtTheDestinationDeadlineCannotAwardAlreadyExpiredCredit() {
        val result = (plan(at = Instant.parse("2028-03-31T17:00:00Z")) as Result.Success).value
        assertEquals(LeaveYearRollover(0, 4), result.rollover)
        assertEquals(6, result.settlement!!.halfDays)
        assertTrue(result.movements.isEmpty())
        val prior = (plan(at = Instant.parse("2028-03-31T16:59:59Z")) as Result.Success).value
        assertEquals(4, prior.rollover.carryHalfDays)
    }

    @Test
    fun missingOrUnconfiguredCarryKeepsLegacyAccountingWithoutInventingADeadline() {
        val legacy =
            (plan(credit = null, terms = policy.copy(carryValidityMonths = null)) as Result.Success)
                .value
        assertEquals(LeaveYearRollover(4, 6), legacy.rollover)
        assertNull(legacy.validity)
        assertNull(legacy.settlement)
        assertTrue(legacy.movements.isEmpty())
        val noPolicy = (plan(credit = null, terms = null) as Result.Success).value
        assertEquals(LeaveYearRollover(0, 10), noPolicy.rollover)
    }

    @Test
    fun unresolvedRequestsAndInvalidSourceBalancesCannotProducePartialPlans() {
        assertEquals(
            "leave_resolution_required",
            (plan(pending = true) as Result.Failed).failure.code,
        )
        assertEquals(
            "leave_resolution_required",
            (plan(account = balance.copy(reservedHalfDays = 1)) as Result.Failed).failure.code,
        )
        assertEquals(
            "leave_carry_conflict",
            (plan(account = balance.copy(availableHalfDays = 5)) as Result.Failed).failure.code,
        )
        assertEquals(
            "leave_destination_year_closed",
            (plan(destination = next.copy(closed = true)) as Result.Failed).failure.code,
        )
        assertEquals(
            "leave_year_closed",
            (plan(account = balance.copy(closed = true)) as Result.Failed).failure.code,
        )
    }

    @Test
    fun bothExpiryAndClosingNeedEnoughVersionCapacityBeforeAnyWrite() {
        assertEquals(
            "leave_balance_limit",
            (plan(account = balance.copy(version = 999999996)) as Result.Failed).failure.code,
        )
        assertEquals(
            "leave_carry_conflict",
            (plan(credit = bucket.copy(version = 1000000000)) as Result.Failed).failure.code,
        )
        assertEquals(
            "leave_carry_conflict",
            (plan(credit = bucket.copy(validity = null, version = 999999999)) as Result.Failed)
                .failure
                .code,
        )
    }
}
