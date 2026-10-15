package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class OffboardingScheduleSecurityHttpTest : OffboardingScheduleApiFixture() {
    @Autowired private lateinit var people: PeopleRepository

    @ParameterizedTest
    @ValueSource(strings = ["review", "list", "detail", "schedule", "replay", "cancel", "recover"])
    fun liveManagementPermissionIsRequiredAfterTheCaseWaitIncludingReceiptReplay(action: String) {
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
        val barrier = LifecycleReadProbe.Barrier(f.company)
        lifecycleProbe.caseWait.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    when (action) {
                        "review" -> get(f.browser, "${f.path}/review")
                        "list" -> get(f.browser, f.path)
                        "detail" -> get(f.browser, "${f.path}/${prepared.id}")
                        "cancel",
                        "recover" ->
                            jobCommand(f, prepared.id, action, requireNotNull(commandReview))
                        else -> submit(f, prepared)
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='people.offboard'",
                        f.company,
                        f.actor.accountId,
                    )
                barrier.release.countDown()
                val refused = pending.get(10, TimeUnit.SECONDS)
                assertEquals(403, refused.statusCode(), refused.body())
                assertEquals(
                    "offboarding_access_required",
                    json.readTree(refused.body())["code"].asString(),
                )
                assertEquals(before, count(f, "operation_receipts"))
                assertEquals("ACTIVE", employmentStatus(f))
                assertEquals("OPEN", caseStatus(f))
            } finally {
                barrier.release.countDown()
                lifecycleProbe.caseWait.set(null)
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "credential,session_revoked",
        "permission,offboarding_access_required",
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
            OffboardingScheduleProbe.Hook(f.company) {
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
                                "delete from membership_permissions where company_id=? and account_id=? and permission='people.offboard'",
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
                assertEquals("OPEN", caseStatus(f))
                assertEquals(1, count(f, "employment_revisions"))
                assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
                assertEquals(
                    "FAILED",
                    database()
                        .queryForObject(
                            "select status from offboarding_schedules where company_id=? and id=?",
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

    @Test
    fun aNewAccountLinkRequiresASeparateReviewAndCannotRetargetAScheduledDeparture() {
        val f = fixture(linked = false)
        val prepared = scheduled(f)
        val target = user().also { member(f.company, it) }
        ok(
            command(
                f.browser,
                "/api/v1/companies/${f.company}/employees/${f.employee}/account-link",
                json.writeValueAsString(
                    mapOf(
                        "accountId" to target,
                        "expectedVersion" to 0,
                        "reason" to "Verified account linkage",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        )
        val lease = claim().single()
        val failed = execute(f, lease) as Result.Failed
        assertEquals("offboarding_identity_changed", failed.failure.code)
        assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
        assertEquals("ACTIVE", employmentStatus(f))
        assertTrue(
            database()
                .queryForObject(
                    "select active from company_memberships where company_id=? and account_id=?",
                    Boolean::class.java,
                    f.company,
                    target,
                )!!
        )
        assertEquals("FAILED", details(f, prepared.id, freshReader(f))["status"].asString())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun lastCompanyAdministratorProtectionAppliesAtReviewAndAgainAtExecution(
        afterAdmission: Boolean
    ) {
        val f = fixture(operator = true)
        val prepared = if (afterAdmission) scheduled(f) else null
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'identity.manage')",
                f.company,
                f.targetAccount,
            )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id<>? and permission='identity.manage'",
                f.company,
                f.targetAccount,
            )
        if (prepared == null) {
            val review = ok(get(f.browser, "${f.path}/review"))
            assertTrue(
                review["blockers"]
                    .toList()
                    .map { it.asString() }
                    .contains("last_company_administrator")
            )
            assertEquals(0, count(f, "background_jobs"))
        } else {
            val lease = claim().single()
            val failed = execute(f, lease) as Result.Failed
            assertEquals("last_company_administrator", failed.failure.code)
            assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
        }
        assertEquals("ACTIVE", employmentStatus(f))
    }

    @Test
    fun anotherEmploymentPreservesTheAccountMembershipWhenThisEmploymentEnds() {
        val f = fixture()
        scheduled(f)
        val personId =
            database()
                .queryForObject(
                    "select person_id from employments where company_id=? and id=?",
                    UUID::class.java,
                    f.company,
                    f.employee,
                )!!
        val next = f.lastDay.plusDays(10)
        val other = UUID.randomUUID()
        assertTrue(
            transactions.run(f.actor) {
                people.createForPerson(
                    f.actor,
                    other,
                    "NEXT${other.toString().take(8)}",
                    personId,
                    EmploymentTerms(
                        next,
                        ContractKind.PERMANENT,
                        next,
                        null,
                        EmploymentStatus.ACTIVE,
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                    "Reviewed future employment",
                )
            } is Result.Success
        )
        val lease = claim().single()
        assertEquals(Result.Success(JobStep(1, true)), execute(f, lease))
        assertEquals("ENDED", employmentStatus(f))
        assertTrue(
            database()
                .queryForObject(
                    "select active from company_memberships where company_id=? and account_id=?",
                    Boolean::class.java,
                    f.company,
                    f.targetAccount,
                )!!
        )
        assertEquals(
            "ACTIVE",
            database()
                .queryForObject(
                    "select status from employment_revisions where company_id=? and employment_id=?",
                    String::class.java,
                    f.company,
                    other,
                ),
        )
    }

    @Test
    fun newlyAssignedReportsAndAnExpiredExecutionWindowRequireHumanReview() {
        val f = fixture()
        scheduled(f)
        employee(
            f.browser,
            f.csrf,
            f.company,
            manager = f.employee,
            start = f.lastDay.minusDays(1).toString(),
        )
        val lease = claim().single()
        val failed = execute(f, lease) as Result.Failed
        assertEquals("reporting_reassignment_required", failed.failure.code)
        assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
        assertEquals("ACTIVE", employmentStatus(f))
        val expired = fixture()
        val prepared = scheduled(expired)
        val late = claim().single()
        val limit = Instant.parse(details(expired, prepared.id)["validUntil"].asString())
        clock.set(limit)
        val refused = executeSchedule.execute(expired.actor, late) as Result.Failed
        assertEquals("offboarding_schedule_review_required", refused.failure.code)
        assertEquals(Result.Success(Unit), abortSchedule.execute(late, refused.failure))
        assertEquals("ACTIVE", employmentStatus(expired))
        assertEquals("OPEN", caseStatus(expired))
    }
}
