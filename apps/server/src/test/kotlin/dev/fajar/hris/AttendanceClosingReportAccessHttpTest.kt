package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.repositories.AttendanceClosingReportRepository
import dev.fajar.hris.reporting.domain.usecases.GetAttendanceClosingReport
import java.net.http.HttpResponse
import java.time.YearMonth
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AttendanceClosingReportProbeConfiguration::class)
class AttendanceClosingReportAccessHttpTest : AttendanceClosingReportApiFixture() {
    @Autowired private lateinit var read: GetAttendanceClosingReport
    @Autowired private lateinit var probe: AttendanceClosingReportProbe
    @Autowired private lateinit var reports: AttendanceClosingReportRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    private fun path(f: Fixture) =
        "/api/v1/companies/${f.company}/reports/attendance-closings?from=2026-09&until=2026-10"

    private fun invoke(actor: Actor, reader: GetAttendanceClosingReport = read) =
        reader.execute(actor, YearMonth.parse("2026-09"), YearMonth.parse("2026-10"))

    @Test
    fun finalPublicationCannotMakeAnEarlierReadIncludePartialClosingRows() {
        val f = isolatedFixture()
        val lease = begin(f)
        assertEquals(Result.Success(JobStep(1, false)), closeWorker.execute(f.actor, lease))
        val barrier = AttendanceClosingReportProbe.Barrier(f.company)
        probe.afterRead.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.admin, path(f)) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertEquals(
                    Result.Success(JobStep(2, true)),
                    pool
                        .submit<Result<JobStep>> { closeWorker.execute(f.actor, lease) }
                        .get(4, TimeUnit.SECONDS),
                )
                barrier.release.countDown()
                val before = reportBody(pending.get(10, TimeUnit.SECONDS))
                assertEquals(0, before["totals"]["closedMonths"].asLong())
                assertTrue(before["months"][0]["closing"].isNull)
            } finally {
                barrier.release.countDown()
                probe.afterRead.set(null)
            }
        }
        assertEquals(1, report(f)["totals"]["closedMonths"].asLong())
    }

    @ParameterizedTest
    @ValueSource(strings = ["reports.read", "workforce.read", "credentials"])
    fun accessRemovedWhileWaitingCannotReleaseCounts(mode: String) {
        val f = closedFixture()
        val response =
            waiting(
                f.actor.accountId,
                {
                    if (mode == "credentials")
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.actor.accountId,
                            )
                    else
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                                f.company,
                                f.actor.accountId,
                                mode,
                            )
                },
                { get(f.admin, path(f)) },
            )
        assertTrue(response.statusCode() in listOf(401, 403), response.body())
        assertFalse(response.body().contains("original"))
    }

    @Test
    fun aConcurrentCorrectionCannotMixTheEarlierOriginalAndAdjustedTotals() {
        val f = closedFixture()
        val barrier = AttendanceClosingReportProbe.Barrier(f.company)
        probe.afterRead.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.admin, path(f)) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                pool
                    .submit { adjusted(f, adjustmentBody(adjustmentReview(f))) }
                    .get(4, TimeUnit.SECONDS)
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, response.statusCode(), response.body())
                val before = json.readTree(response.body())
                assertEquals(0, before["totals"]["current"]["acceptedMinutes"].asLong())
                assertEquals(
                    0,
                    before["months"][0]["counts"]["current"]["acceptedMinutes"].asLong(),
                )
            } finally {
                barrier.release.countDown()
                probe.afterRead.set(null)
            }
        }
        assertEquals(
            420,
            json
                .readTree(get(f.admin, path(f)).body())["totals"]["current"]["acceptedMinutes"]
                .asLong(),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["account", "projection"])
    fun mfaExpiryDuringAcquisitionCannotPublishTheAggregate(stage: String) {
        val f = closedFixture()
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        val security = IdentitySecurityPolicy()
        val reader =
            GetAttendanceClosingReport(
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
        val barrier = AttendanceClosingReportProbe.Barrier(f.company)
        if (stage == "projection") probe.afterRead.set(barrier)
        else
            accountProbe.current.set(
                AccountLockProbe.Barrier(f.actor.accountId, barrier.entered, barrier.release)
            )
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor, reader) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    probe.afterRead.set(null)
                    accountProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
        assertInstanceOf(
            Result.Success::class.java,
            invoke(actor.copy(mfaVerifiedAt = clock.instant()), reader),
        )
    }

    @Test
    fun cancellationAndDatabaseFailureReleaseOwnershipForAnExplicitRetry() {
        val f = closedFixture()
        val barrier = AttendanceClosingReportProbe.Barrier(f.company)
        probe.afterRead.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<Result<*>> { invoke(f.actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                barrier.release.countDown()
                assertInstanceOf(
                    Result.Success::class.java,
                    pool.submit<Result<*>> { invoke(f.actor) }.get(10, TimeUnit.SECONDS),
                )
            } finally {
                barrier.release.countDown()
                probe.afterRead.set(null)
            }
        }
        probe.failure.set(IllegalStateException("Private reporting diagnostic"))
        val failed = get(f.admin, path(f))
        assertEquals(500, failed.statusCode(), failed.body())
        assertEquals("database_failure", json.readTree(failed.body())["code"].asString())
        assertFalse(failed.body().contains("Private reporting diagnostic"))
        assertEquals(200, get(f.admin, path(f)).statusCode())
    }
}
