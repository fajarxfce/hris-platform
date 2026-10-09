package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class LeaveAccountingTransactionHttpTest : LeaveAccountingApiFixture() {
    @Test
    fun incompleteGrantOrLateJournalFailureRollsBackTheEntireEntitlement() {
        val f = accountingFixture()
        val payload = accrualBody()
        val key = UUID.randomUUID()
        val tables =
            listOf(
                "leave_accounts",
                "leave_ledger",
                "leave_accrual_years",
                "leave_accrual_postings",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { accountingRows(f, it) }
        accountingProbe.omittedKind = "GRANT"
        assertEquals(409, accrue(f, payload, key).statusCode())
        assertEquals(before, tables.associateWith { accountingRows(f, it) })
        accountingProbe.clear()
        accountingProbe.beforeJournal = {
            if (it.action == "leave.accrual_posted")
                throw DataIntegrityViolationException("Fixture journal failure")
        }
        assertEquals(409, accrue(f, payload, key).statusCode())
        assertEquals(before, tables.associateWith { accountingRows(f, it) })
        accountingProbe.clear()
        accountingBody(accrue(f, payload, key))
        assertEquals(1, accountingRows(f, "leave_accrual_postings"))
    }

    @Test
    fun missingCarryOrLateClosingFailureCannotConsumeTheOldBalance() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(adjust(f, "5"))
        val payload = closingBody()
        val key = UUID.randomUUID()
        val tables =
            listOf(
                "leave_accounts",
                "leave_ledger",
                "leave_year_closings",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { accountingRows(f, it) }
        accountingProbe.omittedKind = "CARRY_IN"
        assertEquals(409, closeYear(f, payload, key).statusCode())
        assertEquals(before, tables.associateWith { accountingRows(f, it) })
        assertEquals("5", balance(f)["availableDays"].asString())
        assertFalse(balance(f)["closed"].asBoolean())
        accountingProbe.clear()
        accountingProbe.beforeJournal = {
            if (it.action == "leave.year_closed")
                throw DataIntegrityViolationException("Fixture journal failure")
        }
        assertEquals(409, closeYear(f, payload, key).statusCode())
        assertEquals(before, tables.associateWith { accountingRows(f, it) })
        accountingProbe.clear()
        accountingBody(closeYear(f, payload, key))
        assertEquals("2", entitlementView(f, 2027)["balance"]["availableDays"].asString())
    }

    @Test
    fun retainedAccountingEvidenceAndRlsProtectEveryNewTable() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(accrue(f, accrualBody(month = "2026-12")))
        accountingBody(closeYear(f))
        for (table in
            listOf(
                "leave_accounts",
                "leave_accrual_years",
                "leave_accrual_postings",
                "leave_year_closings",
            )) {
            assertThrows(DataAccessException::class.java) {
                database().update("delete from $table where company_id=?", f.company)
            }
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_accounts set version=version+1,available_half_days=available_half_days+1 where company_id=?",
                    f.company,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_accrual_postings set half_days=half_days+1 where company_id=?",
                    f.company,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_year_closings set reason='rewrite' where company_id=?",
                    f.company,
                )
        }
        val other = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        for (table in
            listOf(
                "leave_accounts",
                "leave_accrual_years",
                "leave_accrual_postings",
                "leave_year_closings",
            )) {
            val count =
                transactions.run(accountingActor(other)) {
                    safeDatabaseCall {
                        runtimeJdbc.queryForObject(
                            "select count(*) from $table where company_id=?",
                            Int::class.java,
                            f.company,
                        )
                    }
                }
            assertEquals(0, (count as Result.Success).value)
        }
    }

    @Test
    fun interruptionAfterGrantRollsBackAndLeavesTheOperationAvailableForRetry() {
        val f = accountingFixture()
        val actor = accountingActor(f)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val before = accountingRows(f, "operation_receipts")
        accountingProbe.beforeJournal = {
            if (it.action == "leave.accrual_posted") {
                Thread.currentThread().interrupt()
                throw InterruptedException("Fixture cancellation")
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    postAccrual.execute(
                        actor,
                        key,
                        id,
                        f.employee,
                        f.type,
                        YearMonth.of(2026, 9),
                        0,
                        1,
                        0,
                        "Monthly entitlement",
                    )
                }
            val error =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertTrue(error.cause is InterruptedException)
        }
        for (table in
            listOf(
                "leave_accounts",
                "leave_ledger",
                "leave_accrual_postings",
                "leave_accrual_years",
                "mobile_sync_changes",
            )) assertEquals(0, accountingRows(f, table))
        assertEquals(before, accountingRows(f, "operation_receipts"))
        accountingProbe.clear()
        assertTrue(
            postAccrual.execute(
                actor,
                key,
                id,
                f.employee,
                f.type,
                YearMonth.of(2026, 9),
                0,
                1,
                0,
                "Monthly entitlement",
            ) is Result.Success
        )
    }
}
