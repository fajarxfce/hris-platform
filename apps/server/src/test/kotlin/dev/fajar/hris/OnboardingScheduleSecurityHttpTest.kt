package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class OnboardingScheduleSecurityHttpTest : OnboardingScheduleApiFixture() {

    @ParameterizedTest
    @ValueSource(strings = ["review", "list", "detail", "schedule", "replay", "cancel", "recover"])
    fun liveManagementPermissionIsRequiredAfterThePeopleWaitIncludingReceiptReplay(action: String) {
        val f = fixture(operator = true)
        val prepared = prepare(f)
        if (action != "schedule") ok(submit(f, prepared))
        if (action == "recover") {
            val lease = claim().single()
            database()
                .update(
                    "update background_jobs set status='FAILED',failure_code='job_attempts_exhausted',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null,version=version+1 where id=?",
                    lease.job.request.id,
                )
        }
        val commandReview =
            if (action in setOf("cancel", "recover")) details(f, prepared.id) else null
        val before = count(f, "operation_receipts")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        scheduleProbe.afterPeopleGuard.set(
            OnboardingScheduleProbe.Hook(f.company) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        )
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    when (action) {
                        "review" -> get(f.browser, f.reviewPath)
                        "list" -> get(f.browser, f.path)
                        "detail" -> get(f.browser, "${f.path}/${prepared.id}")
                        "cancel",
                        "recover" ->
                            jobCommand(f, prepared.id, action, requireNotNull(commandReview))
                        else -> submit(f, prepared)
                    }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='people.lifecycle.manage'",
                        f.company,
                        f.actor.accountId,
                    )
                release.countDown()
                val refused = pending.get(10, TimeUnit.SECONDS)
                assertEquals(403, refused.statusCode(), refused.body())
                assertEquals(
                    "onboarding_access_required",
                    json.readTree(refused.body())["code"].asString(),
                )
                assertEquals(before, count(f, "operation_receipts"))
                assertEquals("ACTIVE", employmentStatus(f))
                assertEquals(0, count(f, "lifecycle_cases"))
            } finally {
                release.countDown()
                scheduleProbe.afterPeopleGuard.set(null)
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "credential,session_revoked",
        "permission,onboarding_access_required",
        "membership,company_access_denied",
        "company,company_access_denied",
    )
    fun authorityRevokedDuringTheWorkerWaitStopsExecutionButNotItsOwnedCleanup(
        change: String,
        code: String,
    ) {
        val f = fixture(operator = true)
        val prepared = scheduled(f)
        val lease = claim().single()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        scheduleProbe.beforeForJob.set(
            OnboardingScheduleProbe.Hook(f.company) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        )
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<JobStep>> { execute(f, lease) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.actor.accountId,
                            )
                    "permission" ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='people.lifecycle.manage'",
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
                    else ->
                        database().update("update companies set active=false where id=?", f.company)
                }
                release.countDown()
                val failed = pending.get(10, TimeUnit.SECONDS) as Result.Failed
                assertEquals(code, failed.failure.code)
                assertEquals("ACTIVE", employmentStatus(f))
                assertEquals(0, count(f, "lifecycle_cases"))
                assertEquals(1, count(f, "employment_revisions"))
                assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
                assertEquals(
                    "FAILED",
                    database()
                        .queryForObject(
                            "select status from onboarding_schedules where company_id=? and id=?",
                            String::class.java,
                            f.company,
                            prepared.id,
                        ),
                )
                assertEquals(
                    code,
                    database()
                        .queryForObject(
                            "select failure_code from background_jobs where id=?",
                            String::class.java,
                            lease.job.request.id,
                        ),
                )
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun replacingTheCapturedCredentialInAMemoryLeaseCannotReviveRevokedAuthorization() {
        val f = fixture(operator = true)
        scheduled(f)
        val original = claim().single()
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                f.actor.accountId,
            )
        val refreshed =
            f.actor.copy(credentialVersion = requireNotNull(f.actor.credentialVersion) + 1)
        val forged =
            original.copy(
                job =
                    original.job.copy(
                        request =
                            original.job.request.copy(
                                credentialVersion = requireNotNull(refreshed.credentialVersion)
                            )
                    )
            )
        clock.set(Instant.now())
        assertEquals(
            Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch")),
            executeSchedule.execute(refreshed, forged),
        )
        assertEquals(
            Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch")),
            abortSchedule.execute(forged, Failure(FailureKind.CONFLICT, "fixture_abort")),
        )
        assertEquals("ACTIVE", employmentStatus(f))
        assertEquals(
            "RUNNING",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    original.job.request.id,
                ),
        )
        assertEquals(
            Result.Success(Unit),
            abortSchedule.execute(original, Failure(FailureKind.UNAUTHENTICATED, "session_revoked")),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["account", "membership", "permission"])
    fun unavailableAssigneeAfterAdmissionStopsCreationAndRetainsTheReview(change: String) {
        val f = fixture()
        val prepared = scheduled(f)
        val lease = claim().single()
        when (change) {
            "account" ->
                database().update("update accounts set active=false where id=?", f.assignee)
            "membership" ->
                database()
                    .update(
                        "update company_memberships set active=false where company_id=? and account_id=?",
                        f.company,
                        f.assignee,
                    )
            else ->
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='people.lifecycle.perform'",
                        f.company,
                        f.assignee,
                    )
        }
        val failed = execute(f, lease) as Result.Failed
        assertEquals("lifecycle_assignee_unavailable", failed.failure.code)
        assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
        assertEquals(0, count(f, "lifecycle_cases"))
        val retained = details(f, prepared.id, freshReader(f))
        assertEquals(f.assignee.toString(), retained["assignees"]["equipment"].asString())
        assertEquals("FAILED", retained["status"].asString())
    }
}
