package dev.fajar.hris.leave.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveCarryAdjustmentPolicyTest {
    private val deadline = Instant.parse("2027-03-31T17:00:00Z")
    private val bucket =
        LeaveCarryBucket(
            UUID(0, 1),
            2027,
            6,
            6,
            validity = LeaveCarryValidity(LocalDate.of(2027, 3, 31), deadline),
        )
    private val balance = LeaveBalance(2027, 10, 0, 0)

    private fun plan(
        delta: Int,
        at: Instant = deadline.minusSeconds(1),
        credit: LeaveCarryBucket? = bucket,
        account: LeaveBalance = balance,
    ): Result<LeaveCarryMovement?> = planLeaveCarryAdjustment(account, credit, delta, at)

    @Test
    fun ordinaryCreditFundsReductionsBeforeCarriedCredit() {
        assertEquals(Result.Success(null), plan(2))
        assertEquals(Result.Success(null), plan(-4))
        assertEquals(
            Result.Success(LeaveCarryMovement(bucket.id, LeaveCarryEffect.DEDUCT, 1)),
            plan(-5),
        )
        assertEquals(
            Result.Success(LeaveCarryMovement(bucket.id, LeaveCarryEffect.DEDUCT, 6)),
            plan(-10),
        )
        assertEquals("insufficient_leave_balance", (plan(-11) as Result.Failed).failure.code)
    }

    @Test
    fun deadlineSettlesExpiredFreeUnitsBeforeCheckingTheAdjustment() {
        val expired = LeaveCarryMovement(bucket.id, LeaveCarryEffect.EXPIRE, 6)
        assertEquals(Result.Success(expired), plan(-4, deadline))
        assertEquals(Result.Success(expired), plan(2, deadline))
        assertEquals(
            "insufficient_leave_balance",
            (plan(-5, deadline) as Result.Failed).failure.code,
        )
        assertEquals(
            Result.Success(expired),
            plan(2, deadline, account = balance.copy(availableHalfDays = Int.MAX_VALUE)),
        )
    }

    @Test
    fun adjustmentsDoNotTouchReservedOrConsumedFunding() {
        val held = bucket.copy(availableHalfDays = 3, reservedHalfDays = 2, consumedHalfDays = 1)
        val account = LeaveBalance(2027, 4, 2, 1)
        val movement = (plan(-2, credit = held, account = account) as Result.Success).value!!
        val changed = (applyLeaveCarryMovement(held, movement) as Result.Success).value
        assertEquals(2, changed.availableHalfDays)
        assertEquals(2, changed.reservedHalfDays)
        assertEquals(1, changed.consumedHalfDays)
        assertEquals(1, changed.deductedHalfDays)
    }

    @Test
    fun unconfiguredValidityAndHistoricalOrdinaryCreditDoNotInventExpiry() {
        assertEquals(Result.Success(null), plan(2, deadline, bucket.copy(validity = null)))
        assertEquals(
            Result.Success(LeaveCarryMovement(bucket.id, LeaveCarryEffect.DEDUCT, 1)),
            plan(-5, deadline, bucket.copy(validity = null)),
        )
        assertEquals(Result.Success(null), plan(-10, deadline, null))
    }

    @Test
    fun inconsistentOrExhaustedBucketsCannotProduceAPartialPlan() {
        for (invalid in
            listOf(
                bucket.copy(year = 2028),
                bucket.copy(initialHalfDays = 7),
                bucket.copy(reservedHalfDays = 1, availableHalfDays = 5),
                bucket.copy(version = 1000000000L),
            )) {
            assertEquals(
                "leave_carry_conflict",
                (plan(-5, credit = invalid) as Result.Failed).failure.code,
            )
        }
        assertEquals(
            "leave_year_closed",
            (plan(2, account = balance.copy(closed = true)) as Result.Failed).failure.code,
        )
        assertEquals(
            "invalid_leave_adjustment",
            (plan(Int.MIN_VALUE) as Result.Failed).failure.code,
        )
    }
}
