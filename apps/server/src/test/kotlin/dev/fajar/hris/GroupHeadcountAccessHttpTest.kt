package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.GroupHeadcountReport
import dev.fajar.hris.reporting.domain.repositories.HeadcountReportRepository
import dev.fajar.hris.reporting.domain.usecases.GetGroupHeadcountReport
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class GroupHeadcountAccessHttpTest : GroupHeadcountApiFixture() {
    @Autowired private lateinit var getReport: GetGroupHeadcountReport
    @Autowired private lateinit var reports: HeadcountReportRepository
    @Autowired private lateinit var policies: CompanyClientPolicyRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var reads: CompanyReadTransactionRunner

    private val date = LocalDate.parse("2026-10-01")
    private val client = ClientRequest(null, false)

    private fun waiting(
        f: Fixture,
        selected: List<UUID>,
        report: GetGroupHeadcountReport = getReport,
        actor: Actor = f.actor.copy(companyId = null, permissions = setOf("companies.create")),
        change: () -> Unit,
    ): Result<GroupHeadcountReport> {
        val barrier = ClientPolicyProbe.Barrier(selected.min(), true)
        policyProbe.beforeLock.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<GroupHeadcountReport>> {
                    report.execute(actor, selected, date, client)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                policyProbe.beforeLock.set(null)
            }
        }
    }

    @Test
    fun pendingGroupReadsRecheckEveryCompanyGrantMembershipCredentialAndAccount() {
        for (mode in
            listOf(
                "reports.read",
                "people.read",
                "company",
                "membership",
                "account",
                "credential",
            )) {
            val f = fixture()
            val second = anotherCompany(f)
            val marker = IllegalStateException("revoked group must not query")
            reportProbe.failure.set(marker)
            val response =
                waiting(f, listOf(second.company, f.company)) {
                    when (mode) {
                        "reports.read",
                        "people.read" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                                    second.company,
                                    f.account,
                                    mode,
                                )
                        "company" ->
                            database()
                                .update(
                                    "update companies set active=false where id=?",
                                    second.company,
                                )
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    second.company,
                                    f.account,
                                )
                        "account" ->
                            database()
                                .update("update accounts set active=false where id=?", f.account)
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.account,
                                )
                    }
                }
            val expected =
                when (mode) {
                    "account",
                    "credential" -> "session_revoked"
                    "company",
                    "membership" -> "company_access_denied"
                    else -> "access_denied"
                }
            assertEquals(expected, (response as Result.Failed).failure.code, mode)
            assertSame(marker, reportProbe.failure.get())
            reportProbe.failure.set(null)
        }
    }

    @Test
    fun groupAvailabilityUsesThePolicyThatCommittedBeforeItsGuardsWereAcquired() {
        val f = fixture()
        val second = anotherCompany(f)
        val result =
            waiting(f, listOf(f.company, second.company)) {
                val saved =
                    savePolicy(second, changes = mapOf("disabledModules" to listOf("REPORTING")))
                assertEquals(200, saved.statusCode(), saved.body())
            }
        val failed = (result as Result.Failed).failure
        assertEquals("company_module_disabled", failed.code)
        assertEquals(second.company.toString(), failed.parameters["companyId"])
    }

    @Test
    fun mfaAssuranceMustStillBeValidAfterAGroupReadWaits() {
        val f = fixture()
        val second = anotherCompany(f)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val strict =
            GetGroupHeadcountReport(
                reports,
                policies,
                companies,
                members,
                identities,
                transactions,
                reads,
                security,
                clock,
            )
        val actor =
            f.actor.copy(
                companyId = null,
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
            )
        val result =
            waiting(f, listOf(f.company, second.company), strict, actor) {
                clock.set(clock.instant().plusSeconds(2))
            }
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
    }

    @Test
    fun sharedGroupReadsAllowOtherSnapshotsAndKeepConcurrentEmploymentChangesCoherent() {
        val f = fixture()
        val second = anotherCompany(f)
        val employee = create(f)
        create(second)
        val selected = listOf(f.company, second.company)
        val barrier = HeadcountReportProbe.Barrier(f.company)
        reportProbe.afterRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { readGroup(f, selected) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                reportProbe.afterRead.set(null)
                assertEquals(2, group(f, selected.reversed())["totals"]["active"].asLong())
                for (company in selected) {
                    assertEquals(
                        false,
                        database()
                            .queryForObject(
                                "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                                Boolean::class.java,
                                "hris:client-policy:$company",
                            ),
                    )
                }
                val changed =
                    revise(
                        f.browser,
                        f.csrf,
                        f.company,
                        employee,
                        0,
                        terms("2026-09-01", status = "SUSPENDED"),
                    )
                assertEquals(200, changed.statusCode(), changed.body())
                val fresh = group(f, selected)
                assertEquals(1, fresh["totals"]["active"].asLong())
                assertEquals(1, fresh["totals"]["suspended"].asLong())
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, response.statusCode(), response.body())
                val original = json.readTree(response.body())
                assertEquals(2, original["totals"]["active"].asLong())
                assertEquals(0, original["totals"]["suspended"].asLong())
            } finally {
                barrier.release.countDown()
                reportProbe.afterRead.set(null)
            }
        }
    }

    @Test
    fun cancelledGroupReadsReleaseAllGuardsAndDiscardTheirLateSnapshot() {
        val f = fixture()
        val second = anotherCompany(f)
        val selected = listOf(f.company, second.company)
        val barrier = HeadcountReportProbe.Barrier(f.company)
        val finished = CountDownLatch(1)
        reportProbe.afterRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit {
                    try {
                        getReport.execute(f.actor.copy(companyId = null), selected, date, client)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertEquals(
                    1,
                    database()
                        .queryForObject(
                            "select count(*) from (select id from accounts where id=? for update nowait) unlocked",
                            Int::class.java,
                            f.account,
                        ),
                )
                for (company in selected) {
                    assertEquals(
                        true,
                        database()
                            .queryForObject(
                                "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                                Boolean::class.java,
                                "hris:client-policy:$company",
                            ),
                    )
                }
            } finally {
                barrier.release.countDown()
                reportProbe.afterRead.set(null)
            }
        }
        assertEquals(0, group(f, selected)["totals"]["employments"].asLong())
    }
}
