package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.JobStep
import dev.fajar.hris.workforce.domain.usecases.AdvanceWorkPeriodClose
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class WorkPeriodAccessHttpTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var advance: AdvanceWorkPeriodClose

    @Test
    fun closeRequiresCurrentAuthorityForBothSubmissionAndReceiptReplay() {
        val f = isolatedFixture()
        val key = UUID.randomUUID()
        val denied =
            waiting(
                f.actor.accountId,
                {
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.close'",
                            f.company,
                            f.actor.accountId,
                        )
                },
            ) {
                close(f, key = key)
            }
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from background_jobs where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.close')",
                f.company,
                f.actor.accountId,
            )
        val saved = close(f, key = key)
        assertEquals(200, saved.statusCode(), saved.body())
        val revoked =
            waiting(
                f.actor.accountId,
                {
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            f.actor.accountId,
                        )
                },
            ) {
                close(f, key = key)
            }
        assertEquals(401, revoked.statusCode(), revoked.body())
    }

    @Test
    fun recoveryRevalidatesBeforeChangingThePeriodOrReturningItsReceipt() {
        val f = isolatedFixture()
        val lease = begin(f)
        database()
            .update(
                "update background_jobs set attempts=8,lease_until=now()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        assertTrue(claim().isEmpty())
        val key = UUID.randomUUID()
        val body =
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to period(f)["version"].asLong(),
                    "reason" to "Explicit recovery",
                )
            )
        val request = { command(f.admin, "${f.path}/periods/2026-09/recover", body, f.csrf, key) }
        val denied =
            waiting(
                f.actor.accountId,
                {
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.close'",
                            f.company,
                            f.actor.accountId,
                        )
                },
                request,
            )
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals("PROCESSING", period(f)["status"].asString())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.close')",
                f.company,
                f.actor.accountId,
            )
        val saved = request()
        assertEquals(200, saved.statusCode(), saved.body())
        assertEquals("REVIEW_REQUIRED", period(f)["status"].asString())
        assertEquals(saved.body(), request().body())
        val revoked =
            waiting(
                f.actor.accountId,
                {
                    database()
                        .update(
                            "update company_memberships set active=false where company_id=? and account_id=?",
                            f.company,
                            f.actor.accountId,
                        )
                },
                request,
            )
        assertEquals(403, revoked.statusCode(), revoked.body())
    }

    @Test
    fun periodAndPublishedSnapshotReadsRecheckTheirLiveScope() {
        for (mode in listOf("periods", "snapshot", "membership", "credentials")) {
            val f = isolatedFixture()
            val lease = begin(f)
            assertEquals(Result.Success(JobStep(1, false)), advance.execute(f.actor, lease))
            assertEquals(Result.Success(JobStep(2, true)), advance.execute(f.actor, lease))
            val path =
                if (mode == "periods") "${f.path}/periods?from=2026-09&until=2026-09"
                else "${f.path}/periods/2026-09/employees/${f.employee}"
            val denied =
                waiting(
                    f.actor.accountId,
                    {
                        when (mode) {
                            "membership" ->
                                database()
                                    .update(
                                        "update company_memberships set active=false where company_id=? and account_id=?",
                                        f.company,
                                        f.actor.accountId,
                                    )
                            "credentials" ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        f.actor.accountId,
                                    )
                            else ->
                                database()
                                    .update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.read'",
                                        f.company,
                                        f.actor.accountId,
                                    )
                        }
                    },
                ) {
                    get(f.admin, path)
                }
            assertEquals(
                when (mode) {
                    "snapshot" -> 404
                    "credentials" -> 401
                    else -> 403
                },
                denied.statusCode(),
                "$mode ${denied.body()}",
            )
        }
    }

    @Test
    fun publicationDoesNotBroadenASelfServiceRequestWhenNewPermissionsArrive() {
        val f = isolatedFixture()
        val other = anotherEmployee(f)
        val lease = begin(f)
        assertTrue(advance.execute(f.actor, lease) is Result.Success)
        assertTrue(advance.execute(f.actor, lease) is Result.Success)
        assertEquals(Result.Success(JobStep(3, true)), advance.execute(f.actor, lease))
        val path = "${f.path}/periods/2026-09/employees/$other"
        val pending =
            waiting(
                f.employeeAccount,
                {
                    database()
                        .update(
                            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.read')",
                            f.company,
                            f.employeeAccount,
                        )
                },
            ) {
                get(f.employeeClient, path)
            }
        assertEquals(404, pending.statusCode(), pending.body())
        val fresh = get(f.employeeClient, path)
        assertEquals(200, fresh.statusCode(), fresh.body())
    }

    @Test
    fun workerRechecksOriginAccessAfterWaitingAndCannotAdoptNewCredentials() {
        for (mode in listOf("permission", "membership", "company", "credentials")) {
            val f = isolatedFixture()
            val lease = begin(f)
            val barrier = AccountLockProbe.Barrier(f.actor.accountId)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<JobStep>> { advance.execute(f.actor, lease) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (mode) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.close'",
                                    f.company,
                                    f.actor.accountId,
                                )
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    f.company,
                                    f.actor.accountId,
                                )
                        "company" ->
                            database()
                                .update("update companies set active=false where id=?", f.company)
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.actor.accountId,
                                )
                    }
                    barrier.release.countDown()
                    val result = pending.get(10, TimeUnit.SECONDS)
                    assertTrue(result is Result.Failed, "$mode $result")
                    assertEquals(
                        when (mode) {
                            "permission" -> "access_denied"
                            "credentials" -> "session_revoked"
                            else -> "company_access_denied"
                        },
                        (result as Result.Failed).failure.code,
                    )
                    assertEquals(
                        0,
                        database()
                            .queryForObject(
                                "select count(*) from work_period_snapshots where company_id=?",
                                Int::class.java,
                                f.company,
                            ),
                    )
                    assertEquals(
                        0,
                        database()
                            .queryForObject(
                                "select completed_items from background_jobs where id=?",
                                Int::class.java,
                                lease.job.request.id,
                            ),
                    )
                    if (mode == "credentials") {
                        val replaced = advance.execute(f.actor.copy(credentialVersion = 1), lease)
                        assertTrue(replaced is Result.Failed, replaced.toString())
                        assertEquals("job_scope_mismatch", (replaced as Result.Failed).failure.code)
                    }
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            settleFixtureJobs()
        }
    }
}
