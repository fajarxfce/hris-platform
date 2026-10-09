package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollRunAccessHttpTest : PayrollRunApiFixture() {
    @Test
    fun revocationBeforeTheStartCommitsPreventsItsMutationAndReceiptReplay() {
        for (replay in listOf(false, true)) {
            val f = calculationFixture()
            val key = UUID.randomUUID()
            val body = runBody(f)
            if (replay) payrollBody(createRun(f, body, key))
            val before = count(f.people.payroll.company, "operation_receipts")
            val barrier = AccountLockProbe.Barrier(f.people.preparer.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<java.net.http.HttpResponse<String>> { createRun(f, body, key) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.calculate'",
                            f.people.payroll.company,
                            f.people.preparer.account,
                        )
                    barrier.release.countDown()
                    payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(before, count(f.people.payroll.company, "operation_receipts"))
            assertEquals(if (replay) 1 else 0, count(f.people.payroll.company, "payroll_runs"))
        }
    }

    @Test
    fun credentialRevocationAndExpiredRecentAuthenticationAreCheckedAfterWaiting() {
        for (credentials in listOf(true, false)) {
            val f = calculationFixture()
            val barrier = AccountLockProbe.Barrier(f.people.preparer.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<java.net.http.HttpResponse<String>> { createRun(f) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (credentials)
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.people.preparer.account,
                            )
                    else clock.set(clock.instant().plusSeconds(601))
                    barrier.release.countDown()
                    payrollError(
                        pending.get(10, TimeUnit.SECONDS),
                        if (credentials) 401 else 403,
                        if (credentials) "session_revoked" else "recent_authentication_required",
                    )
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                    clock.set(Instant.parse("2026-10-01T00:00:00Z"))
                }
            }
            assertEquals(0, count(f.people.payroll.company, "payroll_runs"))
        }
    }

    @Test
    fun workerRevalidatesOriginalAuthorityAndCanCleanUpAfterRevocation() {
        val f = calculationFixture()
        val lease = beginRun(f)
        val actor = payrollActor(f.people.payroll, f.people.preparer)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<Result<JobStep>> { stepRun(f, lease, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        actor.accountId,
                    )
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked")),
                    result,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(0, count(f.people.payroll.company, "payroll_run_results"))
        assertEquals(
            Result.Success(Unit),
            abortRun.execute(lease, Failure(FailureKind.UNAUTHENTICATED, "session_revoked")),
        )
        assertEquals("STOPPED", runView(f, runId(lease))["run"]["status"].asString())
    }

    @Test
    fun readingARunCannotAcquirePermissionsGrantedDuringAnObsoleteRequest() {
        val f = calculationFixture()
        val lease = beginRun(f)
        val reader =
            payrollMember(f.people.payroll.company, setOf("company.read", "payroll.calculate"))
        val barrier = AccountLockProbe.Barrier(reader.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<java.net.http.HttpResponse<String>> {
                    get(reader.client, runPath(f, runId(lease)))
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.calculate'",
                        f.people.payroll.company,
                        reader.account,
                    )
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.review')",
                        f.people.payroll.company,
                        reader.account,
                    )
                barrier.release.countDown()
                payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        payrollBody(get(reader.client, runPath(f, runId(lease))))
    }
}
