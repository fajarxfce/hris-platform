package dev.fajar.hris.leave.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.people.domain.entities.ContractKind
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveBalancePolicyTest {
    @Test
    fun adjustmentsNeverSpendReservedBalanceOrOverflowAvailableUnits() {
        val balance = LeaveBalance(2026, 1, 4, 6)
        assertTrue(validateLeaveBalanceAdjustment(balance, -2) is Result.Failed)
        assertTrue(validateLeaveBalanceAdjustment(balance, -1) is Result.Success)
        assertTrue(validateLeaveBalanceAdjustment(balance, 0) is Result.Failed)
        assertTrue(validateLeaveBalanceAdjustment(balance, 733) is Result.Failed)
        assertTrue(
            validateLeaveBalanceAdjustment(balance.copy(availableHalfDays = Int.MAX_VALUE), 1)
                is Result.Failed
        )
    }

    @Test
    fun effectivePoliciesRequireBoundedEligibilityAndAtLeastOneContract() {
        val policy = LeavePolicy("Annual leave", true, true, 12, setOf(ContractKind.PERMANENT), 30)
        val type =
            LeaveType(UUID.randomUUID(), "ANNUAL", LocalDate.of(2026, 1, 1), policy, true, 0, 0)
        assertTrue(validateLeaveType(type, "Policy setup") is Result.Success)
        assertTrue(
            validateLeaveType(
                type.copy(policy = policy.copy(allowedContracts = emptySet())),
                "Policy setup",
            )
                is Result.Failed
        )
        assertTrue(
            validateLeaveType(
                type.copy(policy = policy.copy(minServiceMonths = -1)),
                "Policy setup",
            )
                is Result.Failed
        )
        assertTrue(
            validateLeaveType(type.copy(policy = policy.copy(maxRequestDays = 367)), "Policy setup")
                is Result.Failed
        )
        assertTrue(validateLeaveType(type, "") is Result.Failed)
    }
}
