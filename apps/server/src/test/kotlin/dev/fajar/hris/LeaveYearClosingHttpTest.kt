package dev.fajar.hris

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveYearClosingHttpTest : LeaveAccountingApiFixture() {
    @Test
    fun closingCarriesOnlyThePolicyLimitAndPreservesImmutableSourceEvidence() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(adjust(f, "5"))
        val payload = closingBody()
        val key = UUID.randomUUID()
        val receipt = accountingBody(closeYear(f, payload, key))
        val source = entitlementView(f)
        val closing = source["closing"]
        assertTrue(source["balance"]["closed"].asBoolean())
        assertEquals("0", source["balance"]["availableDays"].asString())
        assertEquals(4, source["balance"]["version"].asLong())
        assertEquals("2", closing["carriedDays"].asString())
        assertEquals("3", closing["expiredDays"].asString())
        assertEquals(1, closing["policy"]["revision"].asLong())
        val destination = entitlementView(f, 2027)
        assertEquals("2", destination["balance"]["availableDays"].asString())
        assertEquals(1, destination["balance"]["version"].asLong())
        accountingBody(accountingPolicy(f, version = 1, carry = "10"))
        assertEquals(receipt, accountingBody(closeYear(f, payload, key)))
        accountingCode(closeYear(f, closingBody(policyVersion = 2)), 409, "leave_year_closed")
        accountingCode(adjust(f, "1", expectedVersion = 4), 409, "leave_year_closed")
        accountingCode(
            accrue(f, accrualBody(month = "2026-12", balanceVersion = 4, policyVersion = 2)),
            409,
            "leave_year_closed",
        )
        assertEquals(4, accountingRows(f, "leave_ledger"))
        assertEquals(1, accountingRows(f, "leave_year_closings"))
    }

    @Test
    fun emptyYearsCloseWithoutFabricatedZeroLedgerEntries() {
        val f =
            accountingFixture(
                Instant.parse("2027-01-05T03:00:00Z"),
                frequency = "MANUAL",
                days = "0",
                carry = "0",
            )
        accountingCode(
            closeYear(f, closingBody(sourceVersion = 0), year = 2027),
            422,
            "leave_year_not_ended",
        )
        accountingBody(closeYear(f, closingBody(sourceVersion = 0, destinationVersion = null)))
        val view = entitlementView(f)
        assertTrue(view["balance"]["closed"].asBoolean())
        assertEquals(1, view["balance"]["version"].asLong())
        assertNotNull(view["balance"]["accountId"])
        assertEquals("0", view["closing"]["expiredDays"].asString())
        assertEquals(0, accountingRows(f, "leave_ledger"))
        assertEquals(1, accountingRows(f, "leave_accounts"))
        assertEquals(1, accountingRows(f, "mobile_sync_changes"))
        assertEquals(0, entitlementView(f, 2027)["balance"]["version"].asLong())
    }

    @Test
    fun pendingRequestsAndCancellationReviewsMustResolveBeforeClosing() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        configureWorkAndApprovals(f)
        accountingBody(adjust(f, "5"))
        val request = UUID.randomUUID()
        accountingBody(submit(f, request, listOf("2026-12-28" to "FULL")))
        accountingCode(
            closeYear(f, closingBody(sourceVersion = 2)),
            409,
            "leave_resolution_required",
        )
        accountingBody(decide(f, request, 0))
        accountingBody(action(f, request, "cancellation", 1))
        assertEquals("0", balance(f)["reservedDays"].asString())
        accountingCode(
            closeYear(f, closingBody(sourceVersion = 3)),
            409,
            "leave_resolution_required",
        )
        accountingBody(decide(f, request, 2, decision = "REJECT"))
        accountingBody(closeYear(f, closingBody(sourceVersion = 3)))
        val view = entitlementView(f)
        assertEquals("1", view["balance"]["consumedDays"].asString())
        assertEquals("1", view["closing"]["consumedDays"].asString())
        assertFalse(
            details(f, request)["availableActions"].iterator().asSequence().any {
                it.asString() == "REQUEST_CANCELLATION"
            }
        )
        accountingCode(action(f, request, "cancellation", 3), 409, "leave_year_closed")
        accountingCode(
            submit(f, UUID.randomUUID(), listOf("2026-12-29" to "FULL")),
            409,
            "leave_year_closed",
        )
    }

    @Test
    fun closedDestinationRejectsCarryWhileZeroCarryDoesNotTouchIt() {
        val f = accountingFixture(Instant.parse("2028-01-05T03:00:00Z"))
        accountingBody(adjust(f, "2"))
        accountingBody(
            closeYear(f, closingBody(sourceVersion = 0, destinationVersion = null), year = 2027)
        )
        accountingCode(closeYear(f), 409, "leave_destination_year_closed")
        accountingBody(accountingPolicy(f, version = 1, carry = "0"))
        accountingBody(closeYear(f, closingBody(policyVersion = 2, destinationVersion = null)))
        assertEquals("2", entitlementView(f)["closing"]["expiredDays"].asString())
        assertEquals(1, entitlementView(f, 2027)["balance"]["version"].asLong())
    }

    @Test
    fun destinationVersionMustMatchBeforeAnyCarryIsPosted() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(adjust(f, "2"))
        accountingBody(accrue(f, accrualBody(month = "2026-12", balanceVersion = 1)))
        accountingCode(
            closeYear(f, closingBody(sourceVersion = 2, destinationVersion = null)),
            409,
            "stale_destination_balance_version",
        )
        assertEquals(0, accountingRows(f, "leave_year_closings"))
        assertEquals("3", balance(f)["availableDays"].asString())
        accountingBody(closeYear(f, closingBody(sourceVersion = 2)))
    }

    @Test
    fun simultaneousAdjustmentsAndClosingCannotApplyToTheSameObservedBalance() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(adjust(f, "3"))
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val closing =
                pool.submit<Int> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    closeYear(f).statusCode()
                }
            val adjustment =
                pool.submit<Int> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    adjust(f, "1", expectedVersion = 1).statusCode()
                }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                assertEquals(
                    listOf(200, 409),
                    listOf(closing.get(10, TimeUnit.SECONDS), adjustment.get(10, TimeUnit.SECONDS))
                        .sorted(),
                )
            } finally {
                start.countDown()
            }
        }
        val state = balance(f)
        if (state["closed"].asBoolean())
            assertEquals("2", entitlementView(f, 2027)["balance"]["availableDays"].asString())
        else {
            assertEquals("4", state["availableDays"].asString())
            assertEquals(0, accountingRows(f, "leave_year_closings"))
        }
    }
}
