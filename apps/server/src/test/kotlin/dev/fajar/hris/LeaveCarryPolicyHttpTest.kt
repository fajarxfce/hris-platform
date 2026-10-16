package dev.fajar.hris

import dev.fajar.hris.leave.delivery.requests.LeaveAccrualPolicyRequest
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class LeaveCarryPolicyHttpTest : LeaveAccountingApiFixture() {
    private fun review(f: LeaveFixture): JsonNode =
        accountingBody(get(f.admin, "/api/v1/companies/${f.company}/leave/policies/${f.type}"))

    @Test
    fun publicValiditySurvivesPolicyUpdatesAndOriginalClosingReplay() {
        val f =
            accountingFixture(
                Instant.parse("2027-01-05T03:00:00Z"),
                carry = "3",
                validityMonths = 3,
            )
        assertEquals(3, review(f)["current"]["accrual"]["carryValidityMonths"].asInt())
        accountingBody(adjust(f, "5"))
        val input = closingBody()
        val operation = UUID.randomUUID()
        val receipt = accountingBody(closeYear(f, input, operation))
        val destination = entitlementView(f, 2027)["balance"]
        assertEquals("3", destination["availableDays"].asString())
        val carry = destination["carry"]
        assertEquals("3", carry["awardedDays"].asString())
        assertEquals("2027-03-31", carry["validThrough"].asString())
        assertEquals("2027-03-31T17:00:00Z", carry["expiresAt"].asString())
        assertEquals("Asia/Jakarta", carry["timezone"].asString())
        val expiry =
            accountingBody(get(f.admin, "/api/v1/companies/${f.company}/leave/carry-expiries"))[
                    "items"]
                .single()
        assertEquals(carry["expiresAt"], expiry["expiresAt"])
        assertEquals("QUEUED", expiry["status"].asString())
        accountingBody(accountingPolicy(f, version = 1, carry = "3", validityMonths = 12))
        val current = review(f)
        assertEquals(12, current["current"]["accrual"]["carryValidityMonths"].asInt())
        val prior = current["history"]["items"].single { it["revision"].asLong() == 1L }
        assertEquals(3, prior["accrual"]["carryValidityMonths"].asInt())
        assertEquals(
            3,
            entitlementView(f)["closing"]["policy"]["accrual"]["carryValidityMonths"].asInt(),
        )
        assertEquals(receipt, accountingBody(closeYear(f, input, operation)))
        assertEquals(destination, entitlementView(f, 2027)["balance"])
        assertEquals(1, accountingRows(f, "leave_carry_expiries"))
    }

    @Test
    fun firstAndLastMonthsKeepTheOriginalCompanyCalendarBoundary() {
        for ((months, date, instant) in
            listOf(
                Triple(1, "2027-01-31", "2027-01-31T17:00:00Z"),
                Triple(12, "2027-12-31", "2027-12-31T17:00:00Z"),
            )) {
            val f =
                accountingFixture(
                    Instant.parse("2027-01-05T03:00:00Z"),
                    carry = "2",
                    validityMonths = months,
                )
            accountingBody(adjust(f, "2"))
            accountingBody(closeYear(f))
            val carry = entitlementView(f, 2027)["balance"]["carry"]
            assertEquals(date, carry["validThrough"].asString())
            assertEquals(instant, carry["expiresAt"].asString())
        }
    }

    @Test
    fun invalidMonthsAndAZeroCarryLimitCannotChangeTheSavedPolicy() {
        val f = accountingFixture()
        for (months in listOf(-1, 0, 13)) {
            val failure =
                accountingCode(
                    accountingPolicy(f, version = 1, validityMonths = months),
                    422,
                    "invalid_leave_accrual_policy",
                )
            assertEquals(
                "out_of_range",
                failure["fields"]["accrual.carryValidityMonths"].asString(),
            )
        }
        accountingCode(
            accountingPolicy(f, version = 1, carry = "0", validityMonths = 3),
            422,
            "invalid_leave_accrual_policy",
        )
        val head = review(f)["current"]
        assertEquals(1, head["version"].asLong())
        assertTrue(head["accrual"]["carryValidityMonths"].isNull)
    }

    @Test
    fun fractionalMonthsAreRejectedWithoutTruncatingOrSavingThem() {
        val f = accountingFixture()
        accountingCode(
            accountingPolicy(f, version = 1, validityMonths = BigDecimal("3.5")),
            400,
            "invalid_request",
        )
        assertEquals(1, review(f)["current"]["version"].asLong())
    }

    @Test
    fun fractionalVersionsCannotBecomeAValidObservedVersion() {
        val f = accountingFixture()
        val response =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${f.type}/2026/adjustments",
                """{"days":"1","expectedVersion":0.5,"reason":"Observed version must be exact"}""",
                f.adminCsrf,
                UUID.randomUUID(),
            )
        accountingCode(response, 400, "invalid_request")
        assertEquals(0, accountingRows(f, "leave_ledger"))
    }

    @Test
    fun omittedAndExplicitNullInputsRetainTheUnlimitedPolicy() {
        for (tail in listOf("", ",\"carryValidityMonths\":null")) {
            val input = """{"frequency":"MANUAL","daysPerPeriod":"0","carryLimitDays":"2"$tail}"""
            assertNull(
                json.readValue(input, LeaveAccrualPolicyRequest::class.java).carryValidityMonths
            )
        }
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(adjust(f, "2"))
        accountingBody(closeYear(f))
        val carry = entitlementView(f, 2027)["balance"]["carry"]
        assertTrue(carry["validThrough"].isNull)
        assertTrue(carry["expiresAt"].isNull)
        assertEquals(0, accountingRows(f, "leave_carry_expiries"))
    }

    @Test
    fun aPublicClosingAfterItsDeadlineDoesNotCreateExpiredCreditOrAnExpiryJob() {
        val f =
            accountingFixture(
                Instant.parse("2027-02-01T00:00:00Z"),
                carry = "2",
                validityMonths = 1,
            )
        accountingBody(adjust(f, "2"))
        accountingBody(closeYear(f))
        val closing = entitlementView(f)["closing"]
        assertEquals("0", closing["carriedDays"].asString())
        assertEquals("2", closing["expiredDays"].asString())
        assertTrue(entitlementView(f, 2027)["balance"]["carry"].isNull)
        assertEquals(0, accountingRows(f, "leave_carry_expiries"))
    }
}
