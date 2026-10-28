package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.repositories.ExpenseReportRepository
import dev.fajar.hris.reporting.domain.usecases.*
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ExpenseReportProbeConfiguration::class)
class ExpenseReportAccessHttpTest : ExpenseReportApiFixture() {
    @Autowired private lateinit var reports: ExpenseReportRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var probe: ExpenseReportProbe
    private val security = IdentitySecurityPolicy(enforceMfa = true)

    @AfterEach
    fun clearReports() {
        probe.afterRead.getAndSet(null)?.release?.countDown()
        probe.failure.set(null)
    }

    private fun reader(f: ExpenseFixture): Actor {
        val account = expenseMember(f.company, listOf("reports.read", "expenses.read"))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        return Actor(
            account,
            f.company,
            setOf("reports.read", "expenses.read"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant(),
        )
    }

    private fun read(rows: Boolean, actor: Actor): Result<*> {
        val from = reportFrom()
        val until = reportUntil()
        return if (rows)
            ListExpenseReportClaims(
                    reports,
                    companies,
                    members,
                    identities,
                    transactions,
                    security,
                    clock,
                )
                .execute(actor, from, until, null, null, 50)
        else
            GetExpenseReport(reports, companies, members, identities, transactions, security, clock)
                .execute(actor, from, until)
    }

    @ParameterizedTest
    @CsvSource(
        "false,reports.read",
        "true,reports.read",
        "false,expenses.read",
        "true,expenses.read",
        "false,credentials",
        "true,credentials",
    )
    fun accessRemovedWhileWaitingCannotReleaseCountsOrRows(rows: Boolean, mode: String) {
        val f = reportFixture()
        val actor = reader(f)
        assertEquals(
            "access_denied",
            (read(rows, actor.copy(permissions = emptySet())) as Result.Failed).failure.code,
        )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(rows, actor) }
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
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @CsvSource("false,false", "true,false", "false,true", "true,true")
    fun assuranceExpiresDuringAdmissionOrAfterAcquisition(rows: Boolean, acquired: Boolean) {
        val f = reportFixture()
        val actor =
            reader(f)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val account = AccountLockProbe.Barrier(actor.accountId)
        val data = ExpenseReportProbe.Barrier(f.company)
        if (acquired) probe.afterRead.set(data) else accountProbe.current.set(account)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(rows, actor) }
            try {
                assertTrue(
                    (if (acquired) data.entered else account.entered).await(5, TimeUnit.SECONDS)
                )
                clock.set(clock.instant().plusSeconds(2))
                account.release.countDown()
                data.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
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
            read(rows, actor.copy(mfaVerifiedAt = clock.instant())),
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun cancellationAndDatabaseFailureReleaseAllOwnershipForAnExplicitRetry(rows: Boolean) {
        val f = reportFixture()
        val actor = reader(f)
        val barrier = ExpenseReportProbe.Barrier(f.company)
        probe.afterRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(rows, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertThrows(CancellationException::class.java) {
                    pending.get(10, TimeUnit.SECONDS)
                }
            } finally {
                barrier.release.countDown()
                probe.afterRead.set(null)
            }
            assertInstanceOf(
                Result.Success::class.java,
                pool.submit<Result<*>> { read(rows, actor) }.get(10, TimeUnit.SECONDS),
            )
        }
        probe.failure.set(IllegalStateException("Private expense diagnostic"))
        val failed = get(f.admin, reportPath(f, rows))
        error(failed, 500, "database_failure")
        assertFalse(failed.body().contains("Private expense diagnostic"))
        assertEquals(200, get(f.admin, reportPath(f, rows)).statusCode())
    }

    @Test
    fun aConcurrentDecisionCannotMixAggregateAndStatusBuckets() {
        val f = reportFixture()
        val id = pendingExpense(f, listOf(reportLine(f, "0.01")))
        val reviewer = reviewer(f)
        val barrier = ExpenseReportProbe.Barrier(f.company)
        probe.afterRead.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.admin, reportPath(f)) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val decision = pool.submit<HttpResponse<String>> { reviewExpense(f, id, reviewer) }
                awaitDecisionGuard(f.company, decision)
                barrier.release.countDown()
                val before = body(pending.get(10, TimeUnit.SECONDS))
                assertEquals("0.01", before["totals"]["amount"].asString())
                assertEquals("0.01", counts(before, "PENDING")["amount"].asString())
                assertEquals(
                    0,
                    java.math.BigDecimal(counts(before, "APPROVED")["amount"].asString()).signum(),
                )
                body(decision.get(10, TimeUnit.SECONDS))
            } finally {
                barrier.release.countDown()
                probe.afterRead.set(null)
            }
        }
        assertEquals("0.01", counts(report(f), "APPROVED")["amount"].asString())
    }

    private fun awaitDecisionGuard(company: UUID, decision: Future<*>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (System.nanoTime() < deadline) {
            val waiting =
                database()
                    .queryForObject(
                        """select exists(select 1 from pg_locks
                    where locktype='advisory' and mode='ExclusiveLock' and not granted
                    and ((classid::bigint << 32) | objid::bigint)=hashtextextended(?,0))""",
                        Boolean::class.java,
                        "company-state:$company",
                    )
            if (waiting == true) return
            if (decision.isDone)
                fail<Unit>("Decision did not retain the company's report read guard")
            Thread.sleep(10)
        }
        fail<Unit>("Decision did not reach the company guard")
    }
}
