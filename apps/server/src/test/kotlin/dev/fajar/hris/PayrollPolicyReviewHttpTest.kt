package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPolicyReviewHttpTest : PayrollApiFixture() {
    @Test
    fun reviewSeparatesUnconfiguredCurrentEffectiveAndHistoricalPolicy() {
        val f = payrollFixture()
        val route = "/api/v1/companies/${f.company}/payroll/policy/review"
        val empty = payrollBody(get(f.operator.client, "$route?asOf=2026-06"))
        assertEquals("2026-06", empty.get("asOf").asString())
        assertTrue(empty.get("current").isNull)
        assertTrue(empty.get("effective").isNull)
        assertEquals(0, empty.get("history").get("items").size())
        assertTrue(empty.get("history").get("nextCursor").isNull)
        payrollBody(policy(f))
        payrollBody(
            policy(
                f,
                policyBody(
                    0,
                    mapOf("effectiveFrom" to "2026-07", "minimumMonthlyWage" to "4100000"),
                ),
            )
        )
        val june = payrollBody(get(f.operator.client, "$route?asOf=2026-06&historyLimit=1"))
        assertEquals(1, june.get("current").get("version").asLong())
        assertEquals(1, june.get("current").get("appliedRevision").asLong())
        assertEquals("4100000", june.get("current").get("minimumMonthlyWage").asString())
        assertEquals(1, june.get("effective").get("version").asLong())
        assertEquals(0, june.get("effective").get("appliedRevision").asLong())
        assertEquals("4000000", june.get("effective").get("minimumMonthlyWage").asString())
        assertEquals("0", june.get("history").get("nextCursor").asString())
        val next =
            payrollBody(get(f.operator.client, "$route?asOf=2026-06&historyLimit=1&historyAfter=0"))
        assertEquals(
            1,
            next.get("history").get("items").get(0).get("policy").get("appliedRevision").asLong(),
        )
        assertTrue(next.get("history").get("nextCursor").isNull)
        val july = payrollBody(get(f.operator.client, "$route?asOf=2026-07"))
        assertEquals(1, july.get("effective").get("appliedRevision").asLong())
        val nextYear = payrollBody(get(f.operator.client, "$route?asOf=2027-01"))
        assertTrue(nextYear.get("effective").isNull)
        assertEquals(june.get("current"), nextYear.get("current"))
        assertEquals(2, nextYear.get("history").get("items").size())
    }

    @Test
    fun reviewRetainsCompanyScopeAndBounds() {
        val f = payrollFixture()
        val route = "/api/v1/companies/${f.company}/payroll/policy/review"
        payrollError(
            get(f.operator.client, "$route?asOf=2026-01&historyAfter=0"),
            422,
            "invalid_page",
        )
        payrollBody(policy(f))
        for (query in
            listOf(
                "historyLimit=0",
                "historyLimit=201",
                "historyAfter=-1",
                "historyAfter=1000",
                "historyAfter=1",
            )) payrollError(
            get(f.operator.client, "$route?asOf=2026-01&$query"),
            422,
            "invalid_page",
        )
        payrollError(get(f.operator.client, "$route?asOf=2023-12"), 422, "invalid_payroll_month")
        payrollError(get(f.operator.client, "$route?asOf=2101-01"), 422, "invalid_payroll_month")
        payrollError(get(f.admin, "$route?asOf=2026-01"), 403, "access_denied")
        payrollError(get(f.owner.client, "$route?asOf=2026-01"), 403, "access_denied")
        val other = payrollFixture()
        payrollError(
            get(other.operator.client, "$route?asOf=2026-01"),
            403,
            "company_access_denied",
        )
        val reader = payrollMember(f.company, setOf("company.read", "payroll.read"))
        payrollBody(get(reader.client, "$route?asOf=2026-01"))
        payrollError(policy(f, policyBody(0), member = reader), 403, "access_denied")
    }

    @Test
    fun sharedReviewKeepsCurrentEffectiveAndHistoryCoherentDuringAnUpdate() {
        val f = payrollFixture()
        payrollBody(policy(f))
        val route = "/api/v1/companies/${f.company}/payroll/policy/review?asOf=2026-06"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writerEntered = CountDownLatch(1)
        val held = AtomicBoolean()
        payrollProbe.afterPolicyRead = { kind, company ->
            if (company == f.company && kind == "current" && held.compareAndSet(false, true)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        payrollProbe.beforePolicyLock = { company, shared ->
            if (company == f.company && !shared) writerEntered.countDown()
        }
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.operator.client, route) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val parallel = payrollBody(get(f.operator.client, route))
                assertEquals(0, parallel.get("current").get("version").asLong())
                val writer =
                    pool.submit<HttpResponse<String>> {
                        policy(f, policyBody(0, mapOf("minimumMonthlyWage" to "4100000")))
                    }
                assertTrue(writerEntered.await(5, TimeUnit.SECONDS))
                release.countDown()
                val reviewed = payrollBody(pending.get(10, TimeUnit.SECONDS))
                assertEquals(0, reviewed.get("current").get("version").asLong())
                assertEquals(0, reviewed.get("effective").get("version").asLong())
                assertEquals(1, reviewed.get("history").get("items").size())
                assertEquals(
                    1,
                    payrollBody(writer.get(10, TimeUnit.SECONDS)).get("version").asLong(),
                )
            } finally {
                release.countDown()
                payrollProbe.clear()
            }
        }
        val renewed = payrollBody(get(f.operator.client, route))
        assertEquals(1, renewed.get("current").get("version").asLong())
        assertEquals(2, renewed.get("history").get("items").size())
    }
}
