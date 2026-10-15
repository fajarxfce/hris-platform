package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

class OffboardingScheduleTransactionHttpTest : OffboardingScheduleApiFixture() {
    @Autowired private lateinit var runtimeJdbc: JdbcTemplate

    private val evidence =
        listOf(
            "employment_revisions",
            "lifecycle_events",
            "audit_entries",
            "outbox_events",
            "operation_receipts",
            "offboarding_schedules",
            "background_jobs",
        )

    @Test
    fun competingRetriesKeepOneAuthorizationAndChangedPayloadCannotReplaceTheReceipt() {
        val f = fixture(future = true)
        val prepared = prepare(f)
        Executors.newFixedThreadPool(2).use { pool ->
            val responses =
                (1..2)
                    .map { pool.submit<HttpResponse<String>> { submit(f, prepared) } }
                    .map { ok(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(responses[0], responses[1])
        }
        val changed =
            prepared.copy(
                body =
                    prepared.body.replace(
                        "Reviewed scheduled departure",
                        "Different reviewed departure",
                    )
            )
        assertEquals(409, submit(f, changed).statusCode())
        val competing =
            prepared.copy(id = UUID.randomUUID(), operation = UUID.randomUUID()).let {
                it.copy(body = it.body.replace(prepared.id.toString(), it.id.toString()))
            }
        val refused = submit(f, competing)
        assertEquals(409, refused.statusCode(), refused.body())
        assertEquals(
            "offboarding_schedule_active",
            json.readTree(refused.body())["code"].asString(),
        )
        assertEquals(1, count(f, "offboarding_schedules"))
        assertEquals(1, count(f, "background_jobs"))
        assertEquals("ACTIVE", employmentStatus(f))
    }

    @Test
    fun twoWorkersWithOneLeaseCannotCommitTwoDepartures() {
        val f = fixture()
        scheduled(f)
        val lease = claim().single()
        clock.set(Instant.now())
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                (1..2)
                    .map {
                        pool.submit<Result<JobStep>> { executeSchedule.execute(f.actor, lease) }
                    }
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it == Result.Success(JobStep(1, true)) })
            assertEquals(
                1,
                results.count {
                    it == Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                },
                results.toString(),
            )
        }
        assertEquals(2, count(f, "employment_revisions"))
        assertEquals("COMPLETED", caseStatus(f))
    }

    @Test
    fun checkpointFailureRollsBackEmploymentAccessCaseAndAllEvidence() {
        val f = fixture()
        val prepared = scheduled(f)
        val lease = claim().single()
        val before = evidence.associateWith { count(f, it) }
        scheduleProbe.failCheckpoint.set(f.company)
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_checkpoint_failure")),
            execute(f, lease),
        )
        assertEquals(before, evidence.associateWith { count(f, it) })
        assertUnchanged(f, prepared.id)
        assertEquals(Result.Success(JobStep(1, true)), execute(f, lease))
        assertEquals(2, count(f, "employment_revisions"))
    }

    @Test
    fun interruptionAfterCheckpointReleasesResourcesAndCannotCommitALateDeparture() {
        val f = fixture()
        val prepared = scheduled(f)
        val lease = claim().single()
        val before = evidence.associateWith { count(f, it) }
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val neverReleased = CountDownLatch(1)
        scheduleProbe.afterCheckpoint.set(
            OffboardingScheduleProbe.Hook(f.company) {
                entered.countDown()
                neverReleased.await()
            }
        )
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<JobStep>> {
                    try {
                        execute(f, lease)
                    } finally {
                        exited.countDown()
                    }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(exited.await(10, TimeUnit.SECONDS))
                assertEquals(before, evidence.associateWith { count(f, it) })
                assertUnchanged(f, prepared.id)
                val retried =
                    pool
                        .submit<Result<JobStep>> {
                            assertFalse(Thread.currentThread().isInterrupted)
                            execute(f, lease)
                        }
                        .get(10, TimeUnit.SECONDS)
                assertEquals(Result.Success(JobStep(1, true)), retried)
            } finally {
                neverReleased.countDown()
                pending.cancel(true)
            }
        }
    }

    @Test
    fun lateLeaseExpiryRollsBackTheCheckpointAndOldOwnersCannotFinishAReplacement() {
        val f = fixture()
        val prepared = scheduled(f)
        val first = claim().single()
        val before = evidence.associateWith { count(f, it) }
        scheduleProbe.afterCheckpoint.set(
            OffboardingScheduleProbe.Hook(f.company) {
                runtimeJdbc.update(
                    "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                    first.job.request.id,
                )
            }
        )
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost")),
            execute(f, first),
        )
        assertEquals(before, evidence.associateWith { count(f, it) })
        assertUnchanged(f, prepared.id)
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                first.job.request.id,
            )
        val replacement = claim().single()
        assertNotEquals(first.token, replacement.token)
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost")),
            execute(f, first),
        )
        assertEquals(
            Result.Success(Unit),
            abortSchedule.execute(first, Failure(FailureKind.UNAVAILABLE, "fixture_late_failure")),
        )
        assertEquals("SCHEDULED", details(f, prepared.id, freshReader(f))["status"].asString())
        assertEquals(Result.Success(JobStep(1, true)), execute(f, replacement))
    }

    @Test
    fun crossingCompanyMidnightDuringJournalingRollsBackTheEffectiveDateAndJobResult() {
        val f = fixture()
        val prepared = scheduled(f)
        val lease = claim().single()
        val before = evidence.associateWith { count(f, it) }
        val nextDay =
            Instant.now()
                .atZone(ZoneId.of(f.timezone))
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZoneId.of(f.timezone))
                .toInstant()
        scheduleProbe.afterJournal.set(
            OffboardingScheduleProbe.JournalHook(f.company, "people.offboarding_completed") {
                clock.set(nextDay)
            }
        )
        val failed = execute(f, lease) as Result.Failed
        assertEquals("offboarding_execution_date_changed", failed.failure.code)
        assertEquals(before, evidence.associateWith { count(f, it) })
        assertUnchanged(f, prepared.id)
        assertEquals(Result.Success(Unit), abortSchedule.execute(lease, failed.failure))
        assertEquals("FAILED", details(f, prepared.id, freshReader(f))["status"].asString())
        assertEquals("ACTIVE", employmentStatus(f))
    }

    @Test
    fun storageRejectsForgedObservedRevisionsAndRollsBackTheQueuedJob() {
        val f = fixture()
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
        val prepared = prepare(f)
        val before = evidence.associateWith { count(f, it) }
        for (field in listOf("effective", "case", "linkage", "job")) {
            scheduleProbe.transformCreate.set(
                OffboardingScheduleProbe.Transform(f.company) {
                    when (field) {
                        "effective" -> it.copy(appliedEmploymentRevision = 0)
                        "case" -> it.copy(caseVersion = it.caseVersion + 1)
                        "linkage" -> it.copy(employeeAccountId = null)
                        else -> it.copy(jobId = UUID.randomUUID())
                    }
                }
            )
            val rejected = submit(f, prepared)
            assertEquals(409, rejected.statusCode(), "$field: ${rejected.body()}")
            assertEquals(before, evidence.associateWith { count(f, it) })
        }
        ok(submit(f, prepared))
    }

    @Test
    fun retainedAuthorizationIsImmutableAndAnotherCompanyCannotReadIt() {
        val f = fixture()
        val prepared = scheduled(f)
        val lease = claim().single()
        for (sql in
            listOf(
                "delete from offboarding_schedules where company_id=?",
                "update offboarding_schedules set reason='Rewritten' where company_id=?",
                "update offboarding_schedules set status='COMPLETED',version=1,finished_at=now() where company_id=?",
            )) assertThrows(DataAccessException::class.java) { database().update(sql, f.company) }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update background_jobs set status='SUCCEEDED',completed_items=1,finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where id=?",
                    lease.job.request.id,
                )
        }
        val other = fixture()
        val hidden =
            transactions.run(other.actor) {
                safeDatabaseCall {
                    runtimeJdbc.queryForObject(
                        "select count(*) from offboarding_schedules where company_id=?",
                        Int::class.java,
                        f.company,
                    )
                }
            }
        assertEquals(Result.Success(0), hidden)
        assertEquals(404, get(other.browser, "${other.path}/${prepared.id}").statusCode())
        assertEquals(Result.Success(JobStep(1, true)), execute(f, lease))
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update offboarding_schedules set finished_at=finished_at+interval '1 second' where company_id=?",
                    f.company,
                )
        }
    }

    private fun assertUnchanged(f: Fixture, id: UUID) {
        assertEquals("ACTIVE", employmentStatus(f))
        assertEquals("OPEN", caseStatus(f))
        val membership =
            database()
                .queryForMap(
                    "select active,version from company_memberships where company_id=? and account_id=?",
                    f.company,
                    f.targetAccount,
                )
        assertEquals(true, membership["active"])
        assertEquals(0L, (membership["version"] as Number).toLong())
        val detail = details(f, id, freshReader(f))
        assertEquals("SCHEDULED", detail["status"].asString())
        assertEquals("RUNNING", detail["job"]["status"].asString())
        assertEquals(0, detail["job"]["completedItems"].asInt())
        assertEquals(0, detail["version"].asLong())
    }
}
