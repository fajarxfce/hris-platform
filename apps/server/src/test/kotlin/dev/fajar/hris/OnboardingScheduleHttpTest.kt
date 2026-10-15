package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OnboardingScheduleHttpTest : OnboardingScheduleApiFixture() {
    @Test
    fun reviewUsesTheFutureEmploymentAndRetainedChecklistWithoutCreatingACase() {
        val f = fixture(future = true)
        val review = ok(get(f.browser, f.reviewPath))
        assertTrue(review["blockers"].isEmpty)
        assertEquals(f.employee.toString(), review["employee"]["id"].asString())
        assertEquals(f.firstDay.toString(), review["targetDate"].asString())
        assertEquals(2, review["template"]["tasks"].size())
        assertEquals(
            f.startsOn.atStartOfDay(ZoneId.of(f.timezone)).toInstant().toString(),
            review["scheduledFor"].asString(),
        )
        assertEquals(
            f.firstDay.plusDays(1).atStartOfDay(ZoneId.of(f.timezone)).toInstant().toString(),
            review["validUntil"].asString(),
        )
        assertTrue(review["basisRevision"].asString().matches(Regex("[a-f0-9]{64}")))
        val prepared = scheduled(f)
        val view = details(f, prepared.id)
        assertEquals("SCHEDULED", view["status"].asString())
        assertEquals("QUEUED", view["job"]["status"].asString())
        assertEquals(f.assignee.toString(), view["assignees"]["equipment"].asString())
        assertEquals(setOf("cancel"), view["actions"].toList().map { it.asString() }.toSet())
        assertEquals(0, count(f, "lifecycle_cases"))
        assertEquals(1, count(f, "employment_revisions"))
        assertEquals(1, ok(get(f.browser, f.path))["items"].size())
        assertTrue(claim().isEmpty())
        for (hidden in
            listOf("credentialVersion", "authenticatedAt", "request", "birthDate", "email")) {
            assertFalse(view.has(hidden), hidden)
            assertFalse(view["job"].has(hidden), hidden)
        }
    }

    @Test
    fun repeatedMidnightReviewCanBeAdmittedByPostgresAtBothWindowBoundaries() {
        val expected = "2026-11-01T05:00:00Z"
        for ((admitted, field) in
            listOf(
                "2026-10-31T12:00:00Z" to "scheduledFor",
                "2026-10-28T12:00:00Z" to "validUntil",
            )) {
            val f =
                fixture(
                    future = true,
                    timezone = "America/Havana",
                    admitted = Instant.parse(admitted),
                )
            val prepared = scheduled(f)
            assertEquals(expected, details(f, prepared.id)[field].asString())
            assertEquals(0, count(f, "lifecycle_cases"))
        }
    }

    @Test
    fun dueExecutionCreatesOneOpenCaseWithPendingTasksAndNeverChangesEmploymentOrAccess() {
        val f = fixture()
        val beforeMemberships =
            database()
                .queryForList(
                    "select account_id,active,version from company_memberships where company_id=? order by account_id",
                    f.company,
                )
        val prepared = scheduled(f)
        val lease = claim().single()
        assertEquals(Result.Success(JobStep(1, true)), execute(f, lease))
        val reader = freshReader(f)
        val view = details(f, prepared.id, reader)
        assertEquals("STARTED", view["status"].asString())
        assertEquals("SUCCEEDED", view["job"]["status"].asString())
        assertEquals(1, view["job"]["completedItems"].asInt())
        assertTrue(view["actions"].isEmpty)
        val created = ok(get(reader, "${api(f.company)}/cases/${prepared.caseId}"))
        assertEquals("OPEN", created["status"].asString())
        assertEquals(0, created["version"].asLong())
        assertEquals(f.firstDay.toString(), created["targetDate"].asString())
        assertEquals(2, created["tasks"].size())
        assertTrue(created["tasks"].all { it["status"].asString() == "PENDING" })
        assertEquals(
            f.assignee.toString(),
            created["tasks"].first { it["key"].asString() == "equipment" }["assigneeId"].asString(),
        )
        assertTrue(
            created["tasks"].first { it["key"].asString() == "welcome" }["assigneeId"].isNull
        )
        assertEquals(1, count(f, "lifecycle_events"))
        assertEquals(1, count(f, "employment_revisions"))
        assertEquals("ACTIVE", employmentStatus(f))
        assertEquals(
            beforeMemberships,
            database()
                .queryForList(
                    "select account_id,active,version from company_memberships where company_id=? order by account_id",
                    f.company,
                ),
        )
        assertEquals("job_lease_lost", (execute(f, lease) as Result.Failed).failure.code)
        assertEquals(
            Result.Success(Unit),
            abortSchedule.execute(lease, Failure(FailureKind.UNAVAILABLE, "delivery_uncertain")),
        )
        val retry = client()
        val csrf = login(retry, f.email)
        val receipt = ok(command(retry, f.path, prepared.body, csrf, prepared.operation))
        assertEquals(prepared.id.toString(), receipt["id"].asString())
        assertEquals(0, receipt["version"].asLong())
        assertEquals(1, count(f, "lifecycle_cases"))
    }

    @Test
    fun cancellationWakesFutureCleanupAndReplaysTheOriginalJobReceipt() {
        val f = fixture(future = true)
        val prepared = scheduled(f)
        val observed = details(f, prepared.id)
        val operation = UUID.randomUUID()
        val receipt = ok(jobCommand(f, prepared.id, "cancel", observed, operation))
        assertEquals(observed["job"]["id"].asString(), receipt["id"].asString())
        assertNotEquals(prepared.id.toString(), receipt["id"].asString())
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
        assertEquals(receipt, ok(jobCommand(f, prepared.id, "cancel", observed, operation)))
        assertEquals(0, count(f, "lifecycle_cases"))
        val next = scheduled(f)
        assertNotEquals(prepared.caseId, next.caseId)
        assertEquals(2, count(f, "onboarding_schedules"))
    }

    @Test
    fun explicitRecoveryClosesAnExhaustedAuthorizationWithoutStartingOrReplacingACase() {
        val f = fixture()
        val prepared = scheduled(f)
        val lease = claim().single()
        database()
            .update(
                "update background_jobs set status='FAILED',failure_code='job_attempts_exhausted',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null,version=version+1 where id=?",
                lease.job.request.id,
            )
        val observed = details(f, prepared.id)
        assertEquals("SCHEDULED", observed["status"].asString())
        assertEquals(setOf("recover"), observed["actions"].toList().map { it.asString() }.toSet())
        val operation = UUID.randomUUID()
        val receipt = ok(jobCommand(f, prepared.id, "recover", observed, operation))
        assertEquals(prepared.id.toString(), receipt["id"].asString())
        assertEquals(1, receipt["version"].asLong())
        assertEquals(receipt, ok(jobCommand(f, prepared.id, "recover", observed, operation)))
        assertEquals("FAILED", details(f, prepared.id)["status"].asString())
        assertEquals(0, count(f, "lifecycle_cases"))
        assertEquals(1, count(f, "background_jobs"))
    }

    @Test
    fun changedTemplateRetainsItsOriginalDefinitionsAndStopsExecution() {
        val f = fixture()
        val prepared = scheduled(f)
        ok(saveTemplate(f.browser, f.csrf, f.company, f.templateId, version = 0, optional = false))
        val view = details(f, prepared.id)
        assertEquals(0, view["template"]["version"].asLong())
        assertEquals(2, view["template"]["tasks"].size())
        val lease = claim().single()
        val failed = execute(f, lease) as Result.Failed
        assertEquals("onboarding_schedule_changed", failed.failure.code)
        assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
        assertEquals("FAILED", details(f, prepared.id, freshReader(f))["status"].asString())
        assertEquals(0, count(f, "lifecycle_cases"))
    }

    @Test
    fun employmentChangesRejectAStaleReviewBeforeQueueAdmission() {
        val f = fixture()
        val prepared = prepare(f)
        ok(
            revise(
                f.browser,
                f.csrf,
                f.company,
                f.employee,
                0,
                terms(f.firstDay.toString(), start = f.firstDay.toString(), status = "PROBATION"),
            )
        )
        val failed = submit(f, prepared)
        assertEquals(409, failed.statusCode(), failed.body())
        assertEquals("stale_employment_version", json.readTree(failed.body())["code"].asString())
        assertEquals(0, count(f, "background_jobs"))
        val current = scheduled(f)
        assertEquals(1, details(f, current.id)["employmentVersion"].asLong())
    }

    @Test
    fun aManualCaseOpenedDuringTheWaitStopsScheduledCreation() {
        val f = fixture()
        val prepared = scheduled(f)
        val manual =
            case(
                f.browser,
                f.csrf,
                f.company,
                f.employee,
                f.templateId,
                date = f.firstDay.toString(),
            )
        val lease = claim().single()
        val failed = execute(f, lease) as Result.Failed
        assertEquals("other_lifecycle_cases_pending", failed.failure.code)
        assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
        assertEquals(1, count(f, "lifecycle_cases"))
        assertNotEquals(prepared.caseId, manual)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun completedManualOnboardingCannotBeStartedAgainByAnAutomaticSchedule(
        afterAdmission: Boolean
    ) {
        val f = fixture()
        val prepared = prepare(f)
        if (afterAdmission) ok(submit(f, prepared))
        val manual =
            case(
                f.browser,
                f.csrf,
                f.company,
                f.employee,
                f.templateId,
                date = f.firstDay.toString(),
            )
        ok(change(f.browser, f.csrf, f.company, manual, 0))
        ok(change(f.browser, f.csrf, f.company, manual, 1, task = "welcome"))
        ok(finish(f.browser, f.csrf, f.company, manual, 2))
        if (afterAdmission) {
            val lease = claim().single()
            val failure = execute(f, lease) as Result.Failed
            assertEquals("onboarding_already_completed", failure.failure.code)
            assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failure.failure))
        } else {
            assertTrue(
                ok(get(f.browser, f.reviewPath))["blockers"].any {
                    it.asString() == "onboarding_already_completed"
                }
            )
            val failure = submit(f, prepared)
            assertEquals(409, failure.statusCode(), failure.body())
            assertEquals(
                "onboarding_already_completed",
                json.readTree(failure.body())["code"].asString(),
            )
            assertEquals(0, count(f, "background_jobs"))
        }
        assertEquals(1, count(f, "lifecycle_cases"))
        assertEquals(1, count(f, "employment_revisions"))
    }

    @Test
    fun unknownAndForeignAssigneesCannotEnterTheQueue() {
        val f = fixture(future = true)
        val foreign = user()
        for ((assignees, code) in
            listOf(
                mapOf("unknown" to f.assignee) to "invalid_lifecycle_assignee",
                mapOf("equipment" to foreign) to "lifecycle_assignee_unavailable",
            )) {
            val failed = submit(f, prepare(f, assignees))
            assertEquals(422, failed.statusCode(), failed.body())
            assertEquals(code, json.readTree(failed.body())["code"].asString())
        }
        assertEquals(0, count(f, "onboarding_schedules"))
        assertEquals(0, count(f, "background_jobs"))
    }

    @Test
    fun theFiniteExecutionWindowCannotBeExtendedByARetry() {
        val f = fixture()
        val prepared = scheduled(f)
        val limit = Instant.parse(details(f, prepared.id)["validUntil"].asString())
        val lease = claim().single()
        clock.set(limit)
        val failed = executeSchedule.execute(f.actor, lease) as Result.Failed
        assertEquals("onboarding_schedule_review_required", failed.failure.code)
        assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
        assertEquals(0, count(f, "lifecycle_cases"))
    }

    @Test
    fun retainedAttemptsHaveABoundedLimitEvenAfterSuccessfulCancellation() {
        val f = fixture(future = true)
        repeat(8) { index ->
            val prepared = scheduled(f)
            assertEquals(index + 1, details(f, prepared.id)["attempt"].asInt())
            ok(jobCommand(f, prepared.id, "cancel"))
            val lease = claim().single()
            assertEquals(
                Result.Success(Unit),
                abortSchedule.execute(lease, Failure(FailureKind.CONFLICT, "job_cancelled")),
            )
        }
        val review = ok(get(f.browser, f.reviewPath))
        assertEquals(
            listOf("onboarding_schedule_limit"),
            review["blockers"].toList().map { it.asString() },
        )
        assertEquals(8, ok(get(f.browser, f.path))["items"].size())
        assertEquals(0, count(f, "lifecycle_cases"))
    }
}
