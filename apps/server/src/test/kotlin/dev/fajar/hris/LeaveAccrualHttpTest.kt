package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveAccrualHttpTest : LeaveAccountingApiFixture() {
    @Test
    fun monthlyGrantHasVersionedEvidenceAndOriginalReplaySurvivesPolicyChanges() {
        val f = accountingFixture()
        val key = UUID.randomUUID()
        val payload = accrualBody()
        val posted = accountingBody(accrue(f, payload, key))
        val view = entitlementView(f)
        assertEquals("1", view["balance"]["availableDays"].asString())
        assertEquals(1, view["balance"]["version"].asLong())
        assertEquals(1, view["postings"].size())
        val evidence = view["postings"][0]
        assertEquals("2026-09-01", evidence["eligibleFrom"].asString())
        assertEquals("2026-09-30", evidence["eligibleUntil"].asString())
        assertEquals(1, evidence["policy"]["revision"].asLong())
        val account =
            accountingBody(
                get(
                    f.worker,
                    "/api/v1/companies/${f.company}/leave/balances/${view["balance"]["accountId"].asString()}",
                )
            )
        assertEquals(1, account["version"].asLong())
        assertEquals(f.employee.toString(), account["employeeId"].asString())
        accountingBody(accountingPolicy(f, version = 1, days = "2"))
        assertEquals(posted, accountingBody(accrue(f, payload, key)))
        accountingCode(
            accrue(f, accrualBody(balanceVersion = 1, policyVersion = 2)),
            409,
            "leave_accrual_already_posted",
        )
        accountingCode(
            accrue(f, accrualBody(month = "2026-08", balanceVersion = 0, policyVersion = 2)),
            409,
            "stale_balance_version",
        )
        accountingCode(
            accrue(f, accrualBody(month = "2026-08", balanceVersion = 1, policyVersion = 1)),
            409,
            "stale_policy_version",
        )
        accountingBody(
            accrue(f, accrualBody(month = "2026-08", balanceVersion = 1, policyVersion = 2))
        )
        assertEquals("3", balance(f)["availableDays"].asString())
        accountingCode(accrue(f, accrualBody(), key), 409, "operation_payload_mismatch")
        assertEquals(2, accountingRows(f, "leave_accrual_postings"))
    }

    @Test
    fun differentOperationIdsCannotGrantOnePeriodTwiceOrIgnoreObservedBalance() {
        val f = accountingFixture()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val jobs =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        accrue(f).statusCode()
                    }
                }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                assertEquals(listOf(200, 409), jobs.map { it.get(10, TimeUnit.SECONDS) }.sorted())
            } finally {
                start.countDown()
            }
        }
        assertEquals(1, accountingRows(f, "leave_accrual_postings"))
        assertEquals(1, accountingRows(f, "leave_ledger"))
        assertEquals(1, balance(f)["version"].asLong())
        val key = UUID.randomUUID()
        val payload = accrualBody(month = "2026-08", balanceVersion = 1)
        Executors.newFixedThreadPool(2).use { pool ->
            val jobs =
                (1..2).map {
                    pool.submit<String> {
                        val r = accrue(f, payload, key)
                        assertEquals(200, r.statusCode(), r.body())
                        r.body()
                    }
                }
            val bodies = jobs.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(bodies[0], bodies[1])
        }
        assertEquals(2, accountingRows(f, "leave_ledger"))
    }

    @Test
    fun annualEntitlementsShareOneYearKeyAndCannotSwitchToMonthlyMidYear() {
        val f = accountingFixture(frequency = "ANNUAL", days = "12")
        accountingBody(accrue(f))
        val view = entitlementView(f)
        assertEquals("2026-01", view["postings"][0]["period"].asString())
        assertEquals("12", view["balance"]["availableDays"].asString())
        accountingCode(
            accrue(f, accrualBody(month = "2026-08", balanceVersion = 1)),
            409,
            "leave_accrual_already_posted",
        )
        accountingBody(accountingPolicy(f, version = 1, frequency = "MONTHLY"))
        val error =
            accountingCode(
                accrue(f, accrualBody(month = "2026-08", balanceVersion = 1, policyVersion = 2)),
                409,
                "leave_accrual_frequency_changed",
            )
        assertEquals("2026", error["parameters"]["year"].asString())
        assertEquals(1, accountingRows(f, "leave_accrual_postings"))
    }

    @Test
    fun accrualRequiresCompletedMonthlyEligibilityAndObservedEmployment() {
        val f = accountingFixture()
        accountingCode(accrue(f, accrualBody(month = "2026-10")), 422, "leave_accrual_not_due")
        accountingCode(
            accrue(f, accrualBody(employmentVersion = 3)),
            409,
            "stale_employment_version",
        )
        accountingBody(
            revise(
                f.admin,
                f.adminCsrf,
                f.company,
                f.employee,
                0,
                terms(
                    from = "2026-09-15",
                    start = "2025-01-01",
                    status = "SUSPENDED",
                    manager = f.manager,
                ),
            )
        )
        accountingCode(
            accrue(f, accrualBody(employmentVersion = 1)),
            422,
            "leave_employee_ineligible",
        )
        accountingBody(accrue(f, accrualBody(month = "2026-08", employmentVersion = 1)))
        assertEquals(1, accountingRows(f, "leave_accrual_postings"))
    }

    @Test
    fun policyBoundsAndExplicitManualModeDoNotInventARecurringGrant() {
        val f = accountingFixture(frequency = "MANUAL", days = "0")
        accountingCode(accrue(f), 422, "leave_accrual_not_configured")
        accountingCode(
            accountingPolicy(f, version = 1, frequency = "MONTHLY", days = "31.5"),
            422,
            "invalid_leave_accrual_policy",
        )
        accountingCode(
            accountingPolicy(f, version = 1, days = "0.5", partial = false),
            422,
            "invalid_leave_accrual_policy",
        )
        accountingCode(
            accountingPolicy(f, version = 1, carry = "366.5"),
            422,
            "invalid_leave_quantity",
        )
        accountingBody(adjust(f, "2"))
        assertEquals("2", balance(f)["availableDays"].asString())
        accountingCode(adjust(f, "1", expectedVersion = 0), 409, "stale_balance_version")
    }
}
