package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class PayrollPolicyHttpTest : PayrollApiFixture() {
    @Test
    fun annualPolicyHistoryAndExpiryAreExplicit() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val body = policyBody()
        val first = payrollBody(policy(f, body, key))
        assertEquals(0, first.get("version").asLong())
        payrollBody(
            policy(
                f,
                policyBody(
                    0,
                    mapOf("effectiveFrom" to "2026-07", "minimumMonthlyWage" to "4100000"),
                ),
            )
        )
        assertEquals(first, payrollBody(policy(f, body, key)))
        val base = "/api/v1/companies/${f.company}/payroll"
        val previous = payrollBody(get(f.operator.client, "$base/policy?asOf=2026-06"))
        assertEquals("4000000", previous.get("minimumMonthlyWage").asString())
        assertEquals(1, previous.get("version").asLong())
        assertEquals(0, previous.get("appliedRevision").asLong())
        assertEquals(
            "4100000",
            payrollBody(get(f.operator.client, "$base/policy?asOf=2026-07"))
                .get("minimumMonthlyWage")
                .asString(),
        )
        payrollError(
            get(f.operator.client, "$base/policy?asOf=2027-01"),
            409,
            "payroll_policy_not_effective",
        )
        val history = payrollBody(get(f.operator.client, "$base/policy/history?limit=1"))
        assertEquals(1, history.get("items").size())
        val next = history.get("nextCursor").asString()
        assertEquals(
            1,
            payrollBody(get(f.operator.client, "$base/policy/history?after=$next&limit=1"))
                .get("items")
                .size(),
        )
        val rules = payrollBody(get(f.operator.client, "$base/income-tax-rules")).get(0)
        assertEquals(44, rules.get("monthly").get("A").size())
        assertEquals(40, rules.get("monthly").get("B").size())
        assertEquals(41, rules.get("monthly").get("C").size())
    }

    @Test
    fun companyAdministrationDoesNotImplicitlyGrantPayroll() {
        val f = payrollFixture()
        payrollError(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/payroll/policy",
                policyBody(),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            ),
            403,
            "access_denied",
        )
        payrollBody(policy(f))
        payrollError(
            get(f.admin, "/api/v1/companies/${f.company}/payroll/policy?asOf=2026-01"),
            403,
            "access_denied",
        )
        payrollError(
            get(f.owner.client, "/api/v1/companies/${f.company}/payroll/income-tax-rules"),
            403,
            "access_denied",
        )
    }

    @Test
    fun malformedConfigurationAndObsoleteVersionsNeverConsumeTheOperation() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        for (changes in
            listOf(
                mapOf("effectiveUntil" to "2027-01"),
                mapOf("pensionWageCap" to "0"),
                mapOf("reviewReferences" to emptyList<String>()),
                mapOf("incomeTaxRuleId" to "unknown"),
            )) payrollError(
            policy(f, policyBody(changes = changes), key),
            422,
            "invalid_payroll_policy",
        )
        payrollError(
            policy(f, policyBody(changes = mapOf("pensionWageCap" to "1e999")), key),
            422,
            "invalid_decimal_amount",
        )
        assertEquals(0, count(f.company, "payroll_policies"))
        payrollBody(policy(f, key = key))
        payrollError(policy(f, policyBody()), 409, "stale_version")
        payrollError(
            get(
                f.operator.client,
                "/api/v1/companies/${f.company}/payroll/policy/history?limit=201",
            ),
            422,
            "invalid_page",
        )
    }

    @Test
    fun competingPolicyCommandsCommitOneVersionAndOriginalReceipts() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val body = policyBody()
        Executors.newFixedThreadPool(4).use { executor ->
            val results =
                (1..4)
                    .map {
                        executor.submit<java.net.http.HttpResponse<String>> { policy(f, body, key) }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, results.toSet().size)
        }
        assertEquals(1, count(f.company, "payroll_policy_revisions"))
        Executors.newFixedThreadPool(2).use { executor ->
            val results =
                listOf("4200000", "4300000")
                    .map { wage ->
                        executor.submit<java.net.http.HttpResponse<String>> {
                            policy(f, policyBody(0, mapOf("minimumMonthlyWage" to wage)))
                        }
                    }
                    .map { it.get(10, TimeUnit.SECONDS).statusCode() }
            assertEquals(listOf(200, 409), results.sorted())
        }
        assertEquals(2, count(f.company, "payroll_policy_revisions"))
    }

    @Test
    fun journalAndMissingRevisionFailuresRollBackAllConfigurationWrites() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val receipts = count(f.company, "operation_receipts")
        val audit = count(f.company, "audit_entries")
        val outbox = count(f.company, "outbox_events")
        payrollProbe.beforeJournal = {
            if (it.action.startsWith("payroll."))
                throw DataIntegrityViolationException("Fixture failure")
        }
        assertEquals(409, policy(f, key = key).statusCode())
        assertEquals(0, count(f.company, "payroll_policies"))
        assertEquals(receipts, count(f.company, "operation_receipts"))
        assertEquals(audit, count(f.company, "audit_entries"))
        assertEquals(outbox, count(f.company, "outbox_events"))
        payrollProbe.clear()
        payrollProbe.omitPolicyRevision = true
        assertEquals(409, policy(f, key = key).statusCode())
        assertEquals(0, count(f.company, "payroll_policies"))
        assertEquals(receipts, count(f.company, "operation_receipts"))
        payrollProbe.clear()
        payrollBody(policy(f, key = key))
    }

    @Test
    fun databaseEnforcesImmutableVersionsAndCompanyScope() {
        val f = payrollFixture()
        payrollBody(policy(f))
        val other = payrollFixture()
        for (statement in
            listOf(
                "delete from payroll_policies where company_id=?",
                "update payroll_policies set version=2 where company_id=?",
                "delete from payroll_policy_revisions where company_id=?",
                "update payroll_policy_revisions set reason='changed' where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, f.company)
        }
        val rows =
            transactions.run(payrollActor(other)) {
                safeDatabaseCall {
                    runtimeJdbc.queryForObject(
                        "select count(*) from payroll_policy_revisions where company_id=?",
                        Int::class.java,
                        f.company,
                    )!!
                }
            }
        assertEquals(Result.Success(0), rows)
        payrollError(
            get(
                other.operator.client,
                "/api/v1/companies/${f.company}/payroll/policy?asOf=2026-01",
            ),
            403,
            "company_access_denied",
        )
    }
}
