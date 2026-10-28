package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.repositories.PayrollCostReportRepository
import dev.fajar.hris.reporting.domain.usecases.GetPayrollCostReport
import java.net.http.HttpResponse
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(PayrollCostReportProbeConfiguration::class)
class PayrollCostReportAccessHttpTest : PayrollCostReportApiFixture() {
    @Autowired private lateinit var reports: PayrollCostReportRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var probe: PayrollCostReportProbe
    private val security = IdentitySecurityPolicy(enforceMfa = true)

    @AfterEach
    fun clearReport() {
        probe.afterRead.getAndSet(null)?.release?.countDown()
        probe.failure.set(null)
    }

    private fun reader(f: PayrollFixture): Actor {
        val member = payrollMember(f.company, setOf("reports.read", "payroll.read"))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                member.account,
            )
        return Actor(
            member.account,
            f.company,
            setOf("reports.read", "payroll.read"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant(),
        )
    }

    private fun read(actor: Actor): Result<*> =
        GetPayrollCostReport(reports, companies, members, identities, transactions, security, clock)
            .execute(actor, YearMonth.parse("2026-08"), YearMonth.parse("2026-10"))

    @ParameterizedTest
    @ValueSource(strings = ["reports.read", "payroll.read", "credentials"])
    fun revokedOriginalAuthorityCannotSurviveWaitingForAdmission(mode: String) {
        val f = payrollFixture()
        val actor = reader(f)
        assertEquals(
            "access_denied",
            (read(actor.copy(permissions = emptySet())) as Result.Failed).failure.code,
        )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val result = pool.submit<Result<*>> { read(actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                if (mode == "credentials")
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            actor.accountId,
                        )
                else
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                            f.company,
                            actor.accountId,
                            mode,
                        )
                barrier.release.countDown()
                assertEquals(
                    if (mode == "credentials") "session_revoked" else "access_denied",
                    (result.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun expiredMfaBeforeOrAfterTheProjectionCannotReleaseReportData(acquired: Boolean) {
        val f = payrollFixture()
        val actor =
            reader(f)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val account = AccountLockProbe.Barrier(actor.accountId)
        val data = PayrollCostReportProbe.Barrier(f.company)
        if (acquired) probe.afterRead.set(data) else accountProbe.current.set(account)
        Executors.newSingleThreadExecutor().use { pool ->
            val result = pool.submit<Result<*>> { read(actor) }
            try {
                assertTrue(
                    (if (acquired) data.entered else account.entered).await(5, TimeUnit.SECONDS)
                )
                clock.set(clock.instant().plusSeconds(2))
                account.release.countDown()
                data.release.countDown()
                assertEquals(
                    "mfa_required",
                    (result.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                account.release.countDown()
                data.release.countDown()
                accountProbe.current.set(null)
                probe.afterRead.set(null)
            }
        }
        assertInstanceOf(
            Result.Success::class.java,
            read(actor.copy(mfaVerifiedAt = clock.instant())),
        )
    }

    @Test
    fun cancellationAndSanitizedFailureReleaseTheReadForExplicitRetry() {
        val f = payrollFixture()
        val actor = reader(f)
        val barrier = PayrollCostReportProbe.Barrier(f.company)
        probe.afterRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val result = pool.submit<Result<*>> { read(actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(result.cancel(true))
                assertThrows(CancellationException::class.java) { result.get(10, TimeUnit.SECONDS) }
            } finally {
                barrier.release.countDown()
                probe.afterRead.set(null)
            }
            assertInstanceOf(
                Result.Success::class.java,
                pool.submit<Result<*>> { read(actor) }.get(10, TimeUnit.SECONDS),
            )
        }
        probe.failure.set(IllegalStateException("Private payroll cost report diagnostic"))
        val response = get(reportReader(f).client, reportPath(f))
        payrollError(response, 500, "database_failure")
        assertFalse(response.body().contains("Private payroll cost report diagnostic"))
        assertEquals(200, get(reportReader(f).client, reportPath(f)).statusCode())
    }

    @Test
    fun aConcurrentPublishedCorrectionCannotMixEarlierTotalsAndLaterMonthRevisions() {
        val scene = publicationScene()
        val payroll = scene.source.payroll
        val original = report(payroll)
        val barrier = PayrollCostReportProbe.Barrier(payroll.company)
        probe.afterRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    get(reportReader(payroll).client, reportPath(payroll))
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                publish(scene)
                barrier.release.countDown()
                val retained = payrollBody(pending.get(10, TimeUnit.SECONDS))
                assertEquals(original["totals"], retained["totals"])
                assertEquals(original["months"], retained["months"])
            } finally {
                barrier.release.countDown()
                probe.afterRead.set(null)
            }
        }
        val current = report(payroll)
        assertEquals(1, current["totals"]["revisedAssessments"].asLong())
        assertNotEquals(original["totals"]["employerCost"], current["totals"]["employerCost"])
        reconcile(payroll, current)
    }
}
