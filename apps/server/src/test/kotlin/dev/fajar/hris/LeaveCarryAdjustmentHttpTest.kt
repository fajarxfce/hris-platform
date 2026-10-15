package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class LeaveCarryAdjustmentHttpTest : LeaveCarryStorageFixture() {
    private fun change(
        c: PreparedCarry,
        days: String,
        key: UUID = UUID.randomUUID(),
        version: Long = storedBalance(c).version,
    ): HttpResponse<String> =
        command(
            c.f.admin,
            "/api/v1/companies/${c.f.company}/leave/employees/${c.f.employee}/balances/${c.f.type}/2027/adjustments",
            json.writeValueAsString(
                mapOf(
                    "days" to days,
                    "expectedVersion" to version,
                    "reason" to "Reviewed balance correction",
                )
            ),
            c.f.adminCsrf,
            key,
        )

    private fun expired(c: PreparedCarry): PreparedCarry {
        clock.set(carryDeadline)
        return c.copy(f = c.f.copy(adminCsrf = login(c.f.admin)))
    }

    @Test
    fun positiveAndNegativeCorrectionsPreserveOriginalFundingAndReplay() {
        val c = carried()
        accountingBody(change(c, "2"))
        assertEquals(6, storedBucket(c).availableHalfDays)
        accountingBody(change(c, "-1"))
        assertEquals(6, storedBucket(c).availableHalfDays)
        assertEquals(0, storedBucket(c).deductedHalfDays)
        val key = UUID.randomUUID()
        val version = storedBalance(c).version
        val response = change(c, "-2", key, version)
        accountingBody(response)
        assertEquals(response.body(), change(c, "-2", key, version).body())
        assertEquals(4, storedBalance(c).availableHalfDays)
        assertEquals(4, storedBucket(c).availableHalfDays)
        assertEquals(2, storedBucket(c).deductedHalfDays)
        assertEquals(1, accountingRows(c.f, "leave_carry_movements"))
    }

    @Test
    fun deadlineSettlementAndReductionAreOneVersionedCommand() {
        val c = carried()
        accountingBody(change(c, "1"))
        val late = expired(c)
        val version = storedBalance(late).version
        val key = UUID.randomUUID()
        val before = accountingRows(c.f, "leave_ledger")
        val response = change(late, "-1", key, version)
        accountingBody(response)
        assertEquals(response.body(), change(late, "-1", key, version).body())
        assertEquals(0, storedBalance(c).availableHalfDays)
        assertEquals(version + 2, storedBalance(c).version)
        assertEquals(6, storedBucket(c).expiredHalfDays)
        assertEquals(0, storedBucket(c).deductedHalfDays)
        assertEquals(before + 2, accountingRows(c.f, "leave_ledger"))
        assertEquals(1, accountingRows(c.f, "leave_carry_movements"))
    }

    @Test
    fun invalidOrStaleCorrectionDoesNotPartiallyExpireTheBalance() {
        val c = expired(carried())
        val balance = storedBalance(c)
        val bucket = storedBucket(c)
        val before = accountingRows(c.f, "leave_ledger")
        accountingCode(change(c, "-1"), 409, "insufficient_leave_balance")
        accountingCode(change(c, "1", version = balance.version - 1), 409, "stale_balance_version")
        assertEquals(balance, storedBalance(c))
        assertEquals(bucket, storedBucket(c))
        assertEquals(before, accountingRows(c.f, "leave_ledger"))
        accountingBody(change(c, "1"))
        assertEquals(2, storedBalance(c).availableHalfDays)
        assertEquals(6, storedBucket(c).expiredHalfDays)
    }

    @Test
    fun expiredFreeCreditIsSettledWithoutTouchingPriorReservations() {
        val c = carried()
        configureWorkAndApprovals(c.f)
        accountingBody(submit(c.f, UUID.randomUUID(), listOf("2027-03-15" to "FULL")))
        val late = expired(c)
        accountingBody(change(late, "1"))
        assertEquals(2, storedBalance(c).availableHalfDays)
        assertEquals(2, storedBalance(c).reservedHalfDays)
        assertEquals(0, storedBucket(c).availableHalfDays)
        assertEquals(2, storedBucket(c).reservedHalfDays)
        assertEquals(4, storedBucket(c).expiredHalfDays)
    }

    @Test
    fun journalFailureRollsBackExpiryCorrectionAndReceiptTogether() {
        val c = expired(carried())
        val balance = storedBalance(c)
        val bucket = storedBucket(c)
        val tables =
            listOf(
                "leave_carry_movements",
                "leave_ledger",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { accountingRows(c.f, it) }
        val key = UUID.randomUUID()
        accountingProbe.beforeJournal = {
            if (it.action == "leave.balance_adjusted")
                throw DataIntegrityViolationException("Carry correction fixture")
        }
        try {
            assertEquals(409, change(c, "1", key, balance.version).statusCode())
            assertEquals(balance, storedBalance(c))
            assertEquals(bucket, storedBucket(c))
            assertEquals(before, tables.associateWith { accountingRows(c.f, it) })
        } finally {
            accountingProbe.clear()
        }
        accountingBody(change(c, "1", key, balance.version))
        assertEquals(2, storedBalance(c).availableHalfDays)
        assertEquals(6, storedBucket(c).expiredHalfDays)
    }

    @Test
    fun competingCorrectionsCannotBothUseTheObservedBalance() {
        val c = carried()
        val version = storedBalance(c).version
        Executors.newFixedThreadPool(2).use { pool ->
            val first = pool.submit<HttpResponse<String>> { change(c, "-2", version = version) }
            val second = pool.submit<HttpResponse<String>> { change(c, "-2", version = version) }
            val replies = listOf(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
            assertEquals(listOf(200, 409), replies.map { it.statusCode() }.sorted())
            accountingCode(replies.single { it.statusCode() == 409 }, 409, "stale_balance_version")
        }
        assertEquals(2, storedBalance(c).availableHalfDays)
        assertEquals(2, storedBucket(c).availableHalfDays)
        assertEquals(4, storedBucket(c).deductedHalfDays)
        assertEquals(1, accountingRows(c.f, "leave_carry_movements"))
    }
}
