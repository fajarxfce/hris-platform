package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollComparisonData
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.GetPayrollRunComparison
import java.math.BigDecimal
import java.time.Duration
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PayrollRunComparisonHttpTest : PayrollTaxContinuityApiFixture() {
    @Autowired private lateinit var comparison: GetPayrollRunComparison
    @Autowired private lateinit var comparisons: PayrollComparisonRepository
    @Autowired private lateinit var runs: PayrollRunRepository
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var companies: CompanyRepository

    private data class PairFixture(
        val first: PublicationFixture,
        val current: CalculationFixture,
        val run: UUID,
    )

    private fun pair(): PairFixture {
        val first = calculationFixture()
        reviewTemplate(first)
        val (published, _) = publishTaxRun(first, beginTaxRun(first))
        val current =
            followingMonth(first, YearMonth.of(2026, 10)) { f ->
                payrollBody(
                    compensation(
                        f.payroll,
                        compensationBody(
                            version = 0,
                            changes = mapOf("effectiveFrom" to "2026-10"),
                            termChanges =
                                mapOf(
                                    "payBasis" to runPayBasis(),
                                    "basicSalary" to "12000000",
                                    "insuranceWage" to "12500000",
                                ),
                        ),
                    )
                )
            }
        val lease = beginTaxRun(current)
        drainRun(current, lease)
        assertEquals(0, runView(current, runId(lease))["run"]["failed"].asInt())
        return PairFixture(published, current, runId(lease))
    }

    private fun path(p: PairFixture, suffix: String = "") =
        runPath(p.current, p.run) + "/comparison?baselineId=${p.first.run}" + suffix

    @Test
    fun comparesRetainedCompletedPeriodsWithDecimalTotalsAndNoMutations() {
        val p = pair()
        val f = p.current
        val company = f.people.payroll.company
        val before =
            listOf("audit_entries", "operation_receipts", "payroll_run_results").associateWith {
                count(company, it)
            }
        val view = payrollBody(get(f.people.reviewer.client, path(p)))
        assertEquals("2026-09", view["baseline"]["earningsMonth"].asString())
        assertEquals("FINALIZED", view["baseline"]["status"].asString())
        assertEquals("2026-10", view["current"]["earningsMonth"].asString())
        assertEquals("CALCULATED", view["current"]["status"].asString())
        assertEquals(1, view["summary"]["matched"].asInt())
        assertEquals(1, view["employees"]["items"].size())
        val item = view["employees"]["items"][0]
        assertEquals(f.people.payroll.employee.toString(), item["employmentId"].asString())
        val first = runView(f, p.first.run)["results"]["items"][0]
        val current = runView(f, p.run)["results"]["items"][0]
        for (key in listOf("taxableGross", "withheld", "takeHome")) {
            val expected = BigDecimal(current[key].asString()) - BigDecimal(first[key].asString())
            assertEquals(0, expected.compareTo(BigDecimal(item["delta"][key].asString())))
            assertEquals(
                0,
                expected.compareTo(BigDecimal(view["summary"]["delta"][key].asString())),
            )
            assertEquals(
                0,
                BigDecimal(first[key].asString())
                    .compareTo(BigDecimal(view["summary"]["baseline"][key].asString())),
            )
        }
        assertFalse(item.has("facts"))
        assertFalse(item.has("accountId"))
        assertFalse(item.has("calculation"))
        val filtered =
            payrollBody(get(f.people.reviewer.client, path(p, "&changedOnly=true&limit=1")))
        assertEquals(view["summary"], filtered["summary"])
        assertTrue(filtered["employees"]["items"].all { it["change"].asString() != "UNCHANGED" })
        assertEquals(
            0,
            payrollBody(
                    get(
                        f.people.reviewer.client,
                        path(p, "&after=ffffffff-ffff-4fff-bfff-ffffffffffff"),
                    )
                )["employees"]["items"]
                .size(),
        )
        assertEquals(before, before.keys.associateWith { count(company, it) })
    }

    @Test
    fun rejectsForeignOrUnavailableRunsAndIntersectsOriginalAndLiveAccess() {
        val p = pair()
        val f = p.current
        payrollError(get(f.people.reviewer.client, path(p, "&limit=201")), 422, "invalid_page")
        payrollError(
            get(f.people.reviewer.client, runPath(f, p.run) + "/comparison?baselineId=${p.run}"),
            422,
            "invalid_payroll_comparison",
        )
        payrollError(
            get(
                f.people.reviewer.client,
                runPath(f, p.first.run) + "/comparison?baselineId=${p.run}",
            ),
            422,
            "invalid_payroll_comparison",
        )
        payrollError(
            get(
                f.people.reviewer.client,
                runPath(f, p.run) + "/comparison?baselineId=${UUID.randomUUID()}",
            ),
            404,
            "payroll_run_not_found",
        )
        val other = payrollMember(payrollFixture().company, setOf("payroll.read"))
        payrollError(get(other.client, path(p)), 403, "company_access_denied")
        val actor = payrollActor(f.people.payroll, f.people.reviewer).copy(permissions = emptySet())
        assertEquals(
            "access_denied",
            (comparison.execute(actor, p.run, p.first.run, null, 50, false) as Result.Failed)
                .failure
                .code,
        )
        val barrier = AccountLockProbe.Barrier(f.people.reviewer.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> {
                    get(f.people.reviewer.client, path(p))
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission in ('payroll.read','payroll.review','payroll.calculate')",
                        f.people.payroll.company,
                        f.people.reviewer.account,
                    )
                barrier.release.countDown()
                payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @Test
    fun incompleteAndFailedEmployeeResultsCannotProduceASuccessfulComparison() {
        val first = calculationFixture()
        reviewTemplate(first)
        val (published, _) = publishTaxRun(first, beginTaxRun(first))
        val next = followingMonth(first, YearMonth.of(2026, 11))
        val lease = beginTaxRun(next)
        val run = runId(lease)
        val path = runPath(next, run) + "/comparison?baselineId=${published.run}"
        payrollError(get(next.people.reviewer.client, path), 409, "payroll_comparison_unavailable")
        drainRun(next, lease)
        assertEquals(1, runView(next, run)["run"]["failed"].asInt())
        payrollError(get(next.people.reviewer.client, path), 409, "payroll_comparison_unavailable")
    }

    @Test
    fun mfaExpiryAfterTheAggregateWasReadCannotDiscloseAmounts() {
        val p = pair()
        val f = p.current
        val actor =
            payrollActor(f.people.payroll, f.people.reviewer).copy(mfaVerifiedAt = clock.instant())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                actor.accountId,
            )
        val delayed =
            object : PayrollComparisonRepository by comparisons {
                override fun compare(
                    company: UUID,
                    baseline: UUID,
                    current: UUID,
                    after: UUID?,
                    limit: Int,
                    changedOnly: Boolean,
                ): Result<PayrollComparisonData> {
                    val result =
                        comparisons.compare(company, baseline, current, after, limit, changedOnly)
                    assertTrue(result is Result.Success)
                    clock.set(clock.instant().plusSeconds(5))
                    return result
                }
            }
        val secured =
            GetPayrollRunComparison(
                runs,
                delayed,
                policies,
                companies,
                members,
                identities,
                transactions,
                clock,
                IdentitySecurityPolicy(
                    maximumMfaAge = Duration.ofSeconds(4),
                    recentAuthenticationAge = Duration.ofSeconds(2),
                ),
            )
        assertEquals(
            "mfa_required",
            (secured.execute(actor, p.run, p.first.run, null, 50, false) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun differentMonthlyCohortsRemainAddedAndRemovedThroughRealCalculationAndPagination() {
        val first = calculationFixture()
        reviewTemplate(first)
        val (published, _) = publishTaxRun(first, beginTaxRun(first))
        lateinit var newEmployee: UUID
        val month = YearMonth.of(2026, 10)
        var current =
            followingMonth(first, month) { f ->
                newEmployee =
                    employee(
                        f.payroll.admin,
                        f.payroll.adminCsrf,
                        f.payroll.company,
                        start = "2026-10-01",
                    )
                payrollBody(
                    compensation(
                        f.payroll,
                        compensationBody(
                            changes = mapOf("effectiveFrom" to "2026-10"),
                            termChanges = mapOf("payBasis" to runPayBasis()),
                        ),
                        employee = newEmployee,
                    )
                )
            }
        val f = current.people
        val base = "/api/v1/companies/${f.payroll.company}/payroll/employees/$newEmployee"
        val days =
            (1..31).map {
                mapOf(
                    "workDate" to month.atDay(it).toString(),
                    "portion" to "FULL",
                    "disposition" to "PAID",
                    "reference" to "Reviewed newcomer calendar",
                )
            }
        payrollBody(
            command(
                f.preparer.client,
                "$base/inputs/$month",
                inputBody(current.work, termChanges = mapOf("dayResolutions" to days)),
                f.preparer.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollBody(
            command(
                f.reviewer.client,
                "$base/inputs/$month/verify",
                """{"expectedVersion":0,"reason":"Independent newcomer input review"}""",
                f.reviewer.csrf,
                UUID.randomUUID(),
            )
        )
        val opening =
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to null,
                    "expectedEmploymentVersion" to 0,
                    "reason" to "Verified new employment without prior income",
                    "terms" to
                        mapOf(
                            "throughMonth" to 9,
                            "residency" to "RESIDENT",
                            "ptkp" to "K0",
                            "reference" to "Fictional newcomer declaration",
                            "history" to
                                mapOf(
                                    "taxableGross" to "0",
                                    "retirementContributions" to "0",
                                    "qualifiedDonations" to "0",
                                    "withheld" to "0",
                                    "employmentMonths" to 0,
                                    "previousEmployerNet" to "0",
                                    "previousEmployerWithheld" to "0",
                                ),
                        ),
                )
            )
        payrollBody(
            command(
                f.preparer.client,
                "$base/tax-openings/2026",
                opening,
                f.preparer.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollBody(
            command(
                f.reviewer.client,
                "$base/tax-openings/2026/verify",
                """{"expectedVersion":0,"reason":"Independent zero opening review"}""",
                f.reviewer.csrf,
                UUID.randomUUID(),
            )
        )
        payrollBody(periodCancel(f, current.period, 0))
        val period =
            payrollBody(
                periodCreate(
                    f,
                    periodBody(
                        f,
                        employees = setOf(newEmployee),
                        changes =
                            mapOf(
                                "earningsMonth" to "2026-10",
                                "plannedPaymentDate" to "2026-11-02",
                            ),
                    ),
                )
            )
        current = current.copy(period = UUID.fromString(period["id"].asString()))
        val lease = beginTaxRun(current)
        drainRun(current, lease)
        val run = runId(lease)
        assertEquals(0, runView(current, run)["run"]["failed"].asInt())
        val path =
            runPath(current, run) +
                "/comparison?baselineId=${published.run}&changedOnly=true&limit=1"
        val page = payrollBody(get(f.reviewer.client, path))
        assertEquals(0, page["summary"]["matched"].asInt())
        assertEquals(1, page["summary"]["added"].asInt())
        assertEquals(1, page["summary"]["removed"].asInt())
        val next =
            payrollBody(
                get(
                    f.reviewer.client,
                    path + "&after=" + page["employees"]["nextCursor"].asString(),
                )
            )
        val rows = listOf(page["employees"]["items"][0], next["employees"]["items"][0])
        assertEquals(setOf("ADDED", "REMOVED"), rows.map { it["change"].asString() }.toSet())
        assertTrue(next["employees"]["nextCursor"].isNull)
        for (key in listOf("taxableGross", "withheld", "takeHome")) assertEquals(
            0,
            rows
                .map { BigDecimal(it["delta"][key].asString()) }
                .reduce(BigDecimal::add)
                .compareTo(BigDecimal(page["summary"]["delta"][key].asString())),
        )
    }
}
