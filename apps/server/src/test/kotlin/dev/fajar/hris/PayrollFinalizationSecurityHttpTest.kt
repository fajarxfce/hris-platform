package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollFinalizationSecurityHttpTest : PayrollFinalizationApiFixture() {
    @Test
    fun permissionRevocationBlocksANewStartAndItsPreviouslyCommittedReplay() {
        for (replay in listOf(false, true)) {
            val f = approved()
            val body = finalizationBody(f)
            val key = UUID.randomUUID()
            if (replay) payrollBody(startFinalization(f, body, key))
            val prior = count(f.calculation.people.payroll.company, "payroll_finalizations")
            val barrier = AccountLockProbe.Barrier(f.finalizer.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<java.net.http.HttpResponse<String>> {
                        startFinalization(f, body, key)
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.finalize'",
                            f.calculation.people.payroll.company,
                            f.finalizer.account,
                        )
                    barrier.release.countDown()
                    payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(
                prior,
                count(f.calculation.people.payroll.company, "payroll_finalizations"),
            )
        }
    }

    @Test
    fun publicationRechecksCredentialsPermissionsAndAuthenticationAgeAfterWaiting() {
        for (change in listOf("credentials", "permission", "authentication")) {
            val f = approved()
            val lease = beginFinalization(f)
            val actor = payrollActor(f.calculation.people.payroll, f.finalizer)
            val barrier = AccountLockProbe.Barrier(f.finalizer.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<JobStep>> { stepFinalization(f, lease, actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (change) {
                        "credentials" ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.finalizer.account,
                                )
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.finalize'",
                                    f.calculation.people.payroll.company,
                                    f.finalizer.account,
                                )
                        else -> clock.set(clock.instant().plusSeconds(601))
                    }
                    barrier.release.countDown()
                    val result = pending.get(10, TimeUnit.SECONDS)
                    assertTrue(result is Result.Failed, result.toString())
                    assertEquals(
                        when (change) {
                            "credentials" -> "session_revoked"
                            "permission" -> "access_denied"
                            else -> "recent_authentication_required"
                        },
                        (result as Result.Failed).failure.code,
                    )
                    assertEquals(
                        Result.Success(Unit),
                        abortFinalization.execute(lease, result.failure),
                    )
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                    clock.set(Instant.parse("2026-10-01T00:00:00Z"))
                }
            }
            assertEquals(0, count(f.calculation.people.payroll.company, "payroll_assessments"))
            assertEquals("CALCULATED", runView(f.calculation, f.run)["run"]["status"].asString())
        }
    }

    @Test
    fun anInflightReadCannotAcquireNewFinancialPermissions() {
        val f = approved()
        val lease = beginFinalization(f)
        val path = finalizationsPath(f) + "/${finalizationId(lease)}"
        val barrier = AccountLockProbe.Barrier(f.finalizer.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> { get(f.finalizer.client, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.finalize'",
                        f.calculation.people.payroll.company,
                        f.finalizer.account,
                    )
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.read')",
                        f.calculation.people.payroll.company,
                        f.finalizer.account,
                    )
                barrier.release.countDown()
                payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        payrollBody(get(f.finalizer.client, path))
    }

    @Test
    fun changedEmploymentCannotReplaceApprovedEvidenceDuringPublication() {
        val f = approved()
        val lease = beginFinalization(f)
        val p = f.calculation.people.payroll
        payrollBody(
            revise(p.admin, p.adminCsrf, p.company, p.employee, 0, terms(from = "2026-10-02"))
        )
        val failure = Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
        assertEquals(failure, stepFinalization(f, lease))
        assertEquals(Result.Success(Unit), abortFinalization.execute(lease, failure.failure))
        assertEquals(0, count(p.company, "payroll_assessments"))
        payrollError(startFinalization(f), 409, "stale_employment_version")
        payrollBody(withdrawReview(f.calculation, f.review, 1, 1))
        payrollBody(abandonRun(f.calculation, f.run))
    }
}
