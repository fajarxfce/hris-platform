package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OffboardingScheduleHttpTest : OffboardingScheduleApiFixture() {
    @Test
    fun reviewShowsReadinessAndCreationDoesNotChangeEmploymentOrCompanyAccess() {
        val f = fixture(future = true, ready = false)
        val review = ok(get(f.browser, "${f.path}/review"))
        assertEquals(
            listOf("required_lifecycle_tasks_pending"),
            review["blockers"].toList().map { it.asString() },
        )
        assertEquals(0, review["case"]["version"].asLong())
        assertEquals(0, review["employmentVersion"].asLong())
        assertEquals(
            f.lastDay.plusDays(1).atStartOfDay(ZoneId.of(f.timezone)).toInstant().toString(),
            review["scheduledFor"].asString(),
        )
        assertTrue(review["basisRevision"].asString().matches(Regex("[a-f0-9]{64}")))
        assertEquals(0, count(f, "background_jobs"))
        val rejected =
            command(
                f.browser,
                f.path,
                json.writeValueAsString(
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "expectedVersion" to 0,
                        "employmentVersion" to 0,
                        "basisRevision" to review["basisRevision"].asString(),
                        "reason" to "Incomplete checklist",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(409, rejected.statusCode(), rejected.body())
        ok(change(f.browser, f.csrf, f.company, f.caseId, 0))
        val prepared = scheduled(f)
        val detail = details(f, prepared.id)
        assertEquals("SCHEDULED", detail["status"].asString())
        assertEquals("QUEUED", detail["job"]["status"].asString())
        assertEquals(setOf("cancel"), detail["actions"].toList().map { it.asString() }.toSet())
        assertEquals(1, detail["caseVersion"].asLong())
        assertEquals(0, detail["employmentVersion"].asLong())
        assertEquals(1, count(f, "background_jobs"))
        assertEquals("OPEN", caseStatus(f))
        assertEquals("ACTIVE", employmentStatus(f))
        assertTrue(
            database()
                .queryForObject(
                    "select active from company_memberships where company_id=? and account_id=?",
                    Boolean::class.java,
                    f.company,
                    f.targetAccount,
                )!!
        )
        assertEquals(1, ok(get(f.browser, f.path))["items"].size())
        for (hidden in
            listOf(
                "employeeAccountId",
                "credentialVersion",
                "authenticatedAt",
                "request",
                "birthDate",
                "email",
            )) {
            assertFalse(detail.has(hidden), hidden)
            assertFalse(detail["job"].has(hidden), hidden)
        }
        assertTrue(
            claim().isEmpty(),
            "A future job must not be claimed before its execution boundary",
        )
    }

    @Test
    fun cancellationWakesAFutureJobAndRetainsItsOriginalJobAcknowledgement() {
        val f = fixture(future = true)
        val prepared = scheduled(f)
        val reviewed = details(f, prepared.id)
        val key = UUID.randomUUID()
        val first = ok(jobCommand(f, prepared.id, "cancel", reviewed, key))
        assertEquals(reviewed["job"]["id"].asString(), first["id"].asString())
        val lease = claim().single()
        assertEquals(
            Result.Success(Unit),
            abortSchedule.execute(
                lease,
                Failure(FailureKind.CONFLICT, "job_cancellation_requested"),
            ),
        )
        val stopped = details(f, prepared.id)
        assertEquals("CANCELLED", stopped["status"].asString())
        assertEquals("CANCELLED", stopped["job"]["status"].asString())
        assertEquals("job_cancelled", stopped["failureCode"].asString())
        assertEquals(first, ok(jobCommand(f, prepared.id, "cancel", reviewed, key)))
        assertEquals("OPEN", caseStatus(f))
        assertEquals("ACTIVE", employmentStatus(f))
        scheduled(f)
        assertEquals(2, count(f, "offboarding_schedules"))
        assertEquals(2, count(f, "background_jobs"))
    }

    @Test
    fun dueExecutionAtomicallyEndsEmploymentAndOnlyItsUnusedCompanyMembership() {
        val f = fixture()
        val otherCompany = company(f.browser, f.csrf)
        member(otherCompany, f.targetAccount!!)
        val originalPermissions =
            database()
                .queryForList(
                    "select permission from membership_permissions where company_id=? and account_id=? order by permission",
                    String::class.java,
                    f.company,
                    f.targetAccount,
                )
        val prepared = scheduled(f)
        val lease = claim().single()
        assertEquals(Result.Success(JobStep(1, true)), execute(f, lease))
        val reader = freshReader(f)
        val detail = details(f, prepared.id, reader)
        assertEquals("COMPLETED", detail["status"].asString())
        assertEquals("SUCCEEDED", detail["job"]["status"].asString())
        assertEquals(1, detail["job"]["completedItems"].asInt())
        assertEquals("COMPLETED", caseStatus(f))
        assertEquals("ENDED", employmentStatus(f))
        val ended =
            database()
                .queryForMap(
                    "select effective_from,end_date,revision from employment_revisions where company_id=? and employment_id=? order by revision desc limit 1",
                    f.company,
                    f.employee,
                )
        assertEquals(
            LocalDate.now(clock.withZone(ZoneId.of(f.timezone))),
            (ended["effective_from"] as java.sql.Date).toLocalDate(),
        )
        assertEquals(f.lastDay, (ended["end_date"] as java.sql.Date).toLocalDate())
        assertEquals(1L, (ended["revision"] as Number).toLong())
        assertFalse(
            database()
                .queryForObject(
                    "select active from company_memberships where company_id=? and account_id=?",
                    Boolean::class.java,
                    f.company,
                    f.targetAccount,
                )!!
        )
        assertTrue(
            database()
                .queryForObject(
                    "select active from company_memberships where company_id=? and account_id=?",
                    Boolean::class.java,
                    otherCompany,
                    f.targetAccount,
                )!!
        )
        assertTrue(
            database()
                .queryForObject(
                    "select active from accounts where id=?",
                    Boolean::class.java,
                    f.targetAccount,
                )!!
        )
        assertEquals(
            originalPermissions,
            database()
                .queryForList(
                    "select permission from membership_permissions where company_id=? and account_id=? order by permission",
                    String::class.java,
                    f.company,
                    f.targetAccount,
                ),
        )
        assertEquals(2, count(f, "employment_revisions"))
        val late = executeSchedule.execute(f.actor, lease)
        assertEquals("job_lease_lost", (late as Result.Failed).failure.code)
        assertEquals(
            Result.Success(Unit),
            abortSchedule.execute(lease, Failure(FailureKind.UNAVAILABLE, "delivery_uncertain")),
        )
        val retryBrowser = client()
        val csrf = login(retryBrowser, f.email)
        val original = ok(command(retryBrowser, f.path, prepared.body, csrf, prepared.operation))
        assertEquals(prepared.id.toString(), original["id"].asString())
        assertEquals(0, original["version"].asLong())
        assertEquals(1, count(f, "offboarding_schedules"))
    }

    @Test
    fun staleReviewAndLaterChecklistReopeningCannotRetargetTheAuthorization() {
        val f = fixture()
        val prepared = prepare(f)
        ok(
            revise(
                f.browser,
                f.csrf,
                f.company,
                f.employee,
                0,
                terms(
                    f.lastDay.minusDays(1).toString(),
                    start = f.lastDay.minusMonths(6).toString(),
                ),
            )
        )
        val stale = submit(f, prepared)
        assertEquals(409, stale.statusCode(), stale.body())
        assertEquals("stale_employment_version", json.readTree(stale.body())["code"].asString())
        assertEquals(0, count(f, "background_jobs"))
        val current = scheduled(f)
        ok(change(f.browser, f.csrf, f.company, f.caseId, 1, status = "PENDING"))
        ok(change(f.browser, f.csrf, f.company, f.caseId, 2))
        val lease = claim().single()
        val failure = execute(f, lease) as Result.Failed
        assertEquals("offboarding_schedule_changed", failure.failure.code)
        assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failure.failure))
        val detail = details(f, current.id, freshReader(f))
        assertEquals("FAILED", detail["status"].asString())
        assertEquals("offboarding_schedule_changed", detail["failureCode"].asString())
        assertEquals("ACTIVE", employmentStatus(f))
        assertEquals("OPEN", caseStatus(f))
    }

    @Test
    fun explicitRecoveryClosesAnExhaustedJobWithoutQueuingAnotherAttempt() {
        val f = fixture()
        val prepared = scheduled(f)
        val lease = claim().single()
        database()
            .update(
                "update background_jobs set status='FAILED',failure_code='job_attempts_exhausted',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null,version=version+1 where id=?",
                lease.job.request.id,
            )
        val detail = details(f, prepared.id)
        assertEquals("SCHEDULED", detail["status"].asString())
        assertEquals(setOf("recover"), detail["actions"].toList().map { it.asString() }.toSet())
        val key = UUID.randomUUID()
        val receipt = ok(jobCommand(f, prepared.id, "recover", detail, key))
        assertEquals(prepared.id.toString(), receipt["id"].asString())
        assertEquals(1, receipt["version"].asLong())
        assertEquals(receipt, ok(jobCommand(f, prepared.id, "recover", detail, key)))
        val stopped = details(f, prepared.id)
        assertEquals("FAILED", stopped["status"].asString())
        assertEquals("job_attempts_exhausted", stopped["failureCode"].asString())
        assertTrue(stopped["actions"].isEmpty)
        assertEquals(1, count(f, "background_jobs"))
        assertEquals("ACTIVE", employmentStatus(f))
    }

    @Test
    fun manualCompletionCanProceedAfterCancellationWithoutWaitingForAWorker() {
        val f = fixture()
        val prepared = scheduled(f)
        clock.set(Instant.now())
        val browser = client()
        val csrf = login(browser, f.email)
        val current = f.copy(browser = browser, csrf = csrf)
        val blocked = finish(browser, csrf, f.company, f.caseId, 1, offboarding = true)
        assertEquals(409, blocked.statusCode(), blocked.body())
        assertEquals(
            "offboarding_cancellation_required",
            json.readTree(blocked.body())["code"].asString(),
        )
        ok(jobCommand(current, prepared.id, "cancel"))
        ok(finish(browser, csrf, f.company, f.caseId, 1, offboarding = true))
        assertEquals("ENDED", employmentStatus(f))
        assertEquals("COMPLETED", caseStatus(f))
        val lease = claim().single()
        assertEquals(
            Result.Success(Unit),
            abortSchedule.execute(
                lease,
                Failure(FailureKind.CONFLICT, "job_cancellation_requested"),
            ),
        )
        assertEquals("CANCELLED", details(current, prepared.id)["status"].asString())
        assertEquals(2, count(f, "employment_revisions"))
    }

    @Test
    fun cancellingTheCaseRequiresItsActiveExecutionToBeCancelledFirst() {
        val f = fixture(future = true)
        val prepared = scheduled(f)
        val blocked = cancel(f.browser, f.csrf, f.company, f.caseId, 1)
        assertEquals(409, blocked.statusCode(), blocked.body())
        ok(jobCommand(f, prepared.id, "cancel"))
        ok(cancel(f.browser, f.csrf, f.company, f.caseId, 1))
        val lease = claim().single()
        assertEquals(
            Result.Success(Unit),
            abortSchedule.execute(
                lease,
                Failure(FailureKind.CONFLICT, "job_cancellation_requested"),
            ),
        )
        assertEquals("CANCELLED", caseStatus(f))
        assertEquals("ACTIVE", employmentStatus(f))
        assertEquals("CANCELLED", details(f, prepared.id)["status"].asString())
    }
}
