package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.HeadcountReport
import dev.fajar.hris.reporting.domain.repositories.HeadcountReportRepository
import dev.fajar.hris.reporting.domain.usecases.GetHeadcountReport
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class HeadcountReportAccessHttpTest : HeadcountReportApiFixture() {
    @Autowired private lateinit var getReport: GetHeadcountReport

    @Autowired private lateinit var reports: HeadcountReportRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @ValueSource(strings = ["account", "projection"])
    fun companyHeadcountRequiresCurrentMfaAfterBothGuardsAndSourceAcquisition(stage: String) {
        val f = fixture()
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        val security = IdentitySecurityPolicy()
        val reader =
            GetHeadcountReport(
                reports,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val actor =
            f.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val barrier = HeadcountReportProbe.Barrier(f.company)
        if (stage == "projection") reportProbe.afterRead.set(barrier)
        else
            accountProbe.current.set(
                AccountLockProbe.Barrier(f.account, barrier.entered, barrier.release)
            )
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<HeadcountReport>> {
                        reader.execute(actor, LocalDate.parse("2026-10-01"))
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    reportProbe.afterRead.set(null)
                    accountProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
        assertInstanceOf(
            Result.Success::class.java,
            reader.execute(
                actor.copy(mfaVerifiedAt = clock.instant()),
                LocalDate.parse("2026-10-01"),
            ),
        )
    }

    private fun waiting(f: Fixture, change: () -> Unit): Result<HeadcountReport> {
        val barrier = AccountLockProbe.Barrier(f.account)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<HeadcountReport>> {
                    getReport.execute(f.actor, LocalDate.parse("2026-10-01"))
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @Test
    fun permissionLossWhileWaitingPreventsReadingTheCompanyAggregate() {
        for (permission in listOf("reports.read", "people.read")) {
            val f = fixture()
            create(f)
            val response =
                waiting(f) {
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                            f.company,
                            f.account,
                            permission,
                        )
                }
            assertEquals("access_denied", (response as Result.Failed).failure.code)
        }
    }

    @Test
    fun pendingReadsRecheckCompanyMembershipAccountAndCredentialState() {
        for (mode in listOf("company", "membership", "account", "credential")) {
            val f = fixture()
            val response =
                waiting(f) {
                    when (mode) {
                        "company" ->
                            database()
                                .update("update companies set active=false where id=?", f.company)
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    f.company,
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
            assertEquals(
                if (mode in setOf("account", "credential")) "session_revoked"
                else "company_access_denied",
                (response as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun oneSqlSnapshotKeepsItsCountsWhileConcurrentEmploymentChangesCommit() {
        val f = fixture()
        val employee = create(f)
        val barrier = HeadcountReportProbe.Barrier(f.company)
        reportProbe.afterRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> { get(f.browser, "${f.path}?asOf=2026-10-01") }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                reportProbe.afterRead.set(null)
                val revision =
                    revise(
                        f.browser,
                        f.csrf,
                        f.company,
                        employee,
                        0,
                        terms("2026-09-01", status = "SUSPENDED"),
                    )
                assertEquals(200, revision.statusCode(), revision.body())
                val changed = report(f)
                assertEquals(0, changed["active"].asLong())
                assertEquals(1, changed["suspended"].asLong())
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, response.statusCode(), response.body())
                val original = json.readTree(response.body())
                assertEquals(1, original["active"].asLong())
                assertEquals(0, original["suspended"].asLong())
                assertEquals(original["employments"].asLong(), changed["employments"].asLong())
            } finally {
                barrier.release.countDown()
                reportProbe.afterRead.set(null)
            }
        }
    }

    @Test
    fun interruptingAPendingReportReleasesItsTransactionAndDiscardsTheLateSnapshot() {
        val f = fixture()
        val barrier = HeadcountReportProbe.Barrier(f.company)
        val finished = CountDownLatch(1)
        reportProbe.afterRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit {
                    try {
                        getReport.execute(f.actor, LocalDate.parse("2026-10-01"))
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
            } finally {
                barrier.release.countDown()
                reportProbe.afterRead.set(null)
            }
        }
        assertEquals(0, report(f)["employments"].asLong())
    }
}
